package de.rptu.cs.exclaim.security;

import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.xml.ParserPool;
import org.apache.commons.lang3.StringUtils;
import org.opensaml.core.config.ConfigurationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistry;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2alg.SigningMethod;
import org.opensaml.saml.saml2.metadata.EntitiesDescriptor;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.Extensions;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.saml2.metadata.KeyDescriptor;
import org.opensaml.saml.saml2.metadata.SingleLogoutService;
import org.opensaml.saml.saml2.metadata.SingleSignOnService;
import org.opensaml.security.credential.UsageType;
import org.opensaml.xmlsec.keyinfo.KeyInfoSupport;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties.AssertingParty;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties.AssertingParty.Singlesignon;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties.Registration;
import org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyProperties.Singlelogout;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.saml2.Saml2Exception;
import org.springframework.security.saml2.core.OpenSamlInitializationService;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.OpenSamlAssertingPartyDetails;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A {@link RelyingPartyRegistrationRepository} with additional features required for federated Shibboleth deployments:
 * <ul>
 * <li>periodically reload the metadata-uri contents (for IdP key rotation)
 * <li>accept a metadata file containing many {@code EntityDescriptor}s and filter for the {@code EntityDescriptor} with
 *     the {@code entityID} we are interested in
 * <li>automatically enable SAML single logout for this relying party if the asserting party supports it
 * </ul>
 * <p>
 * This bean replaces the default one configured by
 * {@link org.springframework.boot.autoconfigure.security.saml2.Saml2RelyingPartyRegistrationConfiguration}. Parts of
 * our implementation is copied from / inspired by that class.
 */
@Component
@Slf4j
public class Saml2RelyingPartyRegistrationRepository implements RelyingPartyRegistrationRepository {
    static {
        OpenSamlInitializationService.initialize();
    }

    private static final Duration REFRESH_INTERVAL = Duration.ofHours(1);
    private static final String LOGOUT_URL = "{baseUrl}/logout/saml2/slo";

    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final ParserPool parserPool;
    private final UnmarshallerFactory unmarshallerFactory;

    private final Saml2RelyingPartyProperties relyingPartyProperties;
    private final TaskScheduler taskScheduler;

    private final Map<String, RelyingPartyRegistration> relyingPartyRegistrations = new ConcurrentHashMap<>();
    private byte retryCounter = 0;

    public Saml2RelyingPartyRegistrationRepository(Saml2RelyingPartyProperties relyingPartyProperties, TaskScheduler taskScheduler) {
        XMLObjectProviderRegistry registry = ConfigurationService.get(XMLObjectProviderRegistry.class);
        parserPool = registry.getParserPool();
        unmarshallerFactory = registry.getUnmarshallerFactory();

        this.relyingPartyProperties = relyingPartyProperties;
        this.taskScheduler = taskScheduler;
        loadMetadata();
    }

    @Override
    @Nullable
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        return relyingPartyRegistrations.get(registrationId);
    }

    public Set<String> getRegistrationIds() {
        return Collections.unmodifiableSet(relyingPartyRegistrations.keySet());
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    private synchronized void loadMetadata() {
        boolean hasException = false;
        boolean requireRefresh = false;
        for (Map.Entry<String, Registration> entry : relyingPartyProperties.getRegistration().entrySet()) {
            try {
                String id = entry.getKey();
                Registration properties = entry.getValue();
                AssertingParty assertingParty = properties.getAssertingparty();
                String metadataUri = assertingParty.getMetadataUri();

                RelyingPartyRegistration.Builder builder;
                if (StringUtils.isNotEmpty(metadataUri)) {
                    builder = fromMetadataLocation(metadataUri, assertingParty.getEntityId()).registrationId(id);
                    requireRefresh = true;
                } else {
                    builder = RelyingPartyRegistration.withRegistrationId(id);
                }
                applyProperties(properties, id).accept(builder);
                RelyingPartyRegistration registration = builder.build();

                if (registration.getAssertingPartyDetails().getWantAuthnRequestsSigned() && registration.getSigningX509Credentials().isEmpty()) {
                    throw new IllegalStateException("Registration id " + id + " wants requests signed, but no signing credentials have been provided!");
                }

                relyingPartyRegistrations.put(id, registration);
                log.info("Successfully loaded SAML 2.0 metadata for registration id {}", id);
            } catch (Exception e) {
                log.error("Failed to refresh SAML 2.0 metadata", e);
                hasException = true;
            }
        }

        if (hasException) {
            taskScheduler.schedule(this::loadMetadata, Instant.now().plus(nextRetryDelay()));
        } else {
            retryCounter = 0;
            if (requireRefresh) {
                taskScheduler.schedule(this::loadMetadata, Instant.now().plus(REFRESH_INTERVAL));
            }
        }
    }

    private Duration nextRetryDelay() {
        return retryCounter == 16 ? Duration.ofMinutes(5) :
            switch (retryCounter++) {
                case 0, 1, 2 -> Duration.ofSeconds(5);
                case 3, 4 -> Duration.ofSeconds(10);
                case 5, 6 -> Duration.ofSeconds(30);
                case 7, 8, 9 -> Duration.ofMinutes(1);
                default -> Duration.ofMinutes(3);
            };
    }

    private Consumer<RelyingPartyRegistration.Builder> applyProperties(Registration properties, String id) {
        AssertingParty assertingParty = properties.getAssertingparty();
        return builder -> builder
            .assertionConsumerServiceLocation(properties.getAcs().getLocation())
            .assertionConsumerServiceBinding(properties.getAcs().getBinding())
            .assertingPartyDetails(details -> {
                PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
                map.from(assertingParty::getEntityId).to(details::entityId);
                Singlesignon singleSignOn = assertingParty.getSinglesignon();
                map.from(singleSignOn::getBinding).to(details::singleSignOnServiceBinding);
                map.from(singleSignOn::getUrl).to(details::singleSignOnServiceLocation);
                // deviation from Spring's default: allow property to overwrite value from metadataUri
                map.from(singleSignOn::isSignRequest).to(details::wantAuthnRequestsSigned);
                Singlelogout singleLogout = assertingParty.getSinglelogout();
                map.from(singleLogout::getUrl).to(details::singleLogoutServiceLocation);
                map.from(singleLogout::getResponseUrl).to(details::singleLogoutServiceResponseLocation);
                map.from(singleLogout::getBinding).to(details::singleLogoutServiceBinding);
            })
            .signingX509Credentials(credentials ->
                properties.getSigning().getCredentials().forEach(credentialProperties -> credentials.add(
                    new Saml2X509Credential(
                        readPrivateKey(Objects.requireNonNull(
                            credentialProperties.getPrivateKeyLocation(),
                            "Registration id " + id + " is missing the signing private key location!"
                        )),
                        readCertificate(Objects.requireNonNull(
                            credentialProperties.getCertificateLocation(),
                            "Registration id " + id + " is missing the signing certificate location!"
                        )),
                        Saml2X509Credential.Saml2X509CredentialType.SIGNING
                    )
                ))
            )
            .decryptionX509Credentials(credentials ->
                properties.getDecryption().getCredentials().forEach(credentialProperties -> credentials.add(
                    new Saml2X509Credential(
                        readPrivateKey(Objects.requireNonNull(
                            credentialProperties.getPrivateKeyLocation(),
                            "Registration id " + id + " is missing the decryption private key location!"
                        )),
                        readCertificate(Objects.requireNonNull(
                            credentialProperties.getCertificateLocation(),
                            "Registration id " + id + " is missing the decryption certificate location!"
                        )),
                        Saml2X509Credential.Saml2X509CredentialType.DECRYPTION
                    )
                ))
            )
            .assertingPartyDetails(details -> details.verificationX509Credentials(credentials ->
                assertingParty.getVerification().getCredentials().forEach(credentialProperties -> credentials.add(
                    new Saml2X509Credential(
                        readCertificate(Objects.requireNonNull(
                            credentialProperties.getCertificateLocation(),
                            "Registration id " + id + " is missing the asserting party verification certificate location!"
                        )),
                        Saml2X509Credential.Saml2X509CredentialType.ENCRYPTION,
                        Saml2X509Credential.Saml2X509CredentialType.VERIFICATION
                    )
                ))
            ))
            .entityId(properties.getEntityId())
            // deviation from Spring's default: set default settings for single logout if supported by asserting party
            .assertingPartyDetails(details -> {
                boolean hasSLO = StringUtils.isNotEmpty(details.build().getSingleLogoutServiceLocation());
                builder
                    .singleLogoutServiceLocation(defaultIfNull(properties.getSinglelogout().getUrl(), hasSLO ? LOGOUT_URL : null))
                    .singleLogoutServiceResponseLocation(defaultIfNull(properties.getSinglelogout().getResponseUrl(), hasSLO ? LOGOUT_URL : null))
                    .singleLogoutServiceBinding(defaultIfNull(properties.getSinglelogout().getBinding(), hasSLO ? Saml2MessageBinding.POST : null));
            });
    }

    /**
     * Like {@link org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations#fromMetadataLocation},
     * but allow to filter for a specific entityId.
     */
    private RelyingPartyRegistration.Builder fromMetadataLocation(String metadataLocation, @Nullable String entityId) throws Exception {
        try (InputStream inputStream = resourceLoader.getResource(metadataLocation).getInputStream()) {
            Element element = parserPool.parse(inputStream).getDocumentElement();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            if (unmarshaller == null) {
                throw new Saml2Exception("Unsupported element of type " + element.getTagName());
            }
            XMLObject xmlObject = unmarshaller.unmarshall(element);
            List<EntityDescriptor> descriptors;
            if (xmlObject instanceof EntitiesDescriptor entitiesDescriptor) {
                descriptors = entitiesDescriptor.getEntityDescriptors();
            } else if (xmlObject instanceof EntityDescriptor descriptor) {
                descriptors = List.of(descriptor);
            } else {
                throw new Saml2Exception("Unsupported element of type " + xmlObject.getClass());
            }

            // deviation from Spring's default: if the entityId is provided in the properties, then we filter for it
            if (StringUtils.isNotEmpty(entityId)) {
                descriptors = descriptors.stream().filter(d -> entityId.equals(d.getEntityID())).toList();
                if (descriptors.isEmpty()) {
                    throw new Saml2Exception("No EntityDescriptor found for entityId " + entityId);
                }
            }

            int size = descriptors.size();
            if (size == 1) {
                return fromDescriptor(descriptors.get(0));
            }
            throw new Saml2Exception("Need a unique EntityDescriptor, but found " + size);
        }
    }

    public static RelyingPartyRegistration.Builder fromDescriptor(EntityDescriptor descriptor) throws Exception {
        IDPSSODescriptor idpssoDescriptor = descriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
        if (idpssoDescriptor == null) {
            throw new Saml2Exception("Metadata response is missing the necessary IDPSSODescriptor element");
        }
        List<Saml2X509Credential> verification = new ArrayList<>();
        List<Saml2X509Credential> encryption = new ArrayList<>();
        for (KeyDescriptor keyDescriptor : idpssoDescriptor.getKeyDescriptors()) {
            if (keyDescriptor.getUse().equals(UsageType.SIGNING)) {
                for (X509Certificate certificate : KeyInfoSupport.getCertificates(keyDescriptor.getKeyInfo())) {
                    verification.add(Saml2X509Credential.verification(certificate));
                }
            }
            if (keyDescriptor.getUse().equals(UsageType.ENCRYPTION)) {
                for (X509Certificate certificate : KeyInfoSupport.getCertificates(keyDescriptor.getKeyInfo())) {
                    encryption.add(Saml2X509Credential.encryption(certificate));
                }
            }
            if (keyDescriptor.getUse().equals(UsageType.UNSPECIFIED)) {
                for (X509Certificate certificate : KeyInfoSupport.getCertificates(keyDescriptor.getKeyInfo())) {
                    verification.add(Saml2X509Credential.verification(certificate));
                    encryption.add(Saml2X509Credential.encryption(certificate));
                }
            }
        }
        if (verification.isEmpty()) {
            throw new Saml2Exception("Metadata response is missing verification certificates, necessary for verifying SAML assertions");
        }

        RelyingPartyRegistration.AssertingPartyDetails.Builder party = OpenSamlAssertingPartyDetails
            .withEntityDescriptor(descriptor)
            .entityId(descriptor.getEntityID())
            .wantAuthnRequestsSigned(Boolean.TRUE.equals(idpssoDescriptor.getWantAuthnRequestsSigned()))
            .verificationX509Credentials(c -> c.addAll(verification))
            .encryptionX509Credentials(c -> c.addAll(encryption));

        List<SigningMethod> signingMethods = signingMethods(idpssoDescriptor.getExtensions());
        if (signingMethods.isEmpty()) {
            signingMethods = signingMethods(descriptor.getExtensions());
        }
        for (SigningMethod method : signingMethods) {
            party.signingAlgorithms(algorithms -> algorithms.add(method.getAlgorithm()));
        }

        if (idpssoDescriptor.getSingleSignOnServices().isEmpty()) {
            throw new Saml2Exception("Metadata response is missing a SingleSignOnService, necessary for sending AuthnRequests");
        }
        for (SingleSignOnService singleSignOnService : idpssoDescriptor.getSingleSignOnServices()) {
            Saml2MessageBinding binding;
            if (singleSignOnService.getBinding().equals(Saml2MessageBinding.POST.getUrn())) {
                binding = Saml2MessageBinding.POST;
            } else if (singleSignOnService.getBinding().equals(Saml2MessageBinding.REDIRECT.getUrn())) {
                binding = Saml2MessageBinding.REDIRECT;
            } else {
                continue;
            }
            party
                .singleSignOnServiceLocation(singleSignOnService.getLocation())
                .singleSignOnServiceBinding(binding);
            break;
        }

        for (SingleLogoutService singleLogoutService : idpssoDescriptor.getSingleLogoutServices()) {
            Saml2MessageBinding binding;
            if (singleLogoutService.getBinding().equals(Saml2MessageBinding.POST.getUrn())) {
                binding = Saml2MessageBinding.POST;
            } else if (singleLogoutService.getBinding().equals(Saml2MessageBinding.REDIRECT.getUrn())) {
                binding = Saml2MessageBinding.REDIRECT;
            } else {
                continue;
            }
            party
                .singleLogoutServiceLocation(singleLogoutService.getLocation())
                .singleLogoutServiceResponseLocation(defaultIfNull(singleLogoutService.getResponseLocation(), singleLogoutService.getLocation()))
                .singleLogoutServiceBinding(binding);
            break;
        }

        return RelyingPartyRegistration.withAssertingPartyDetails(party.build());
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> signingMethods(@Nullable Extensions extensions) {
        return extensions != null
            ? (List<T>) extensions.getUnknownXMLObjects(SigningMethod.DEFAULT_ELEMENT_NAME)
            : Collections.emptyList();
    }

    private static RSAPrivateKey readPrivateKey(Resource location) {
        try (InputStream inputStream = location.getInputStream()) {
            return Objects.requireNonNull(RsaKeyConverters.pkcs8().convert(inputStream));
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    private static X509Certificate readCertificate(Resource location) {
        try (InputStream inputStream = location.getInputStream()) {
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Nullable
    private static <T> T defaultIfNull(@Nullable T value, @Nullable T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
