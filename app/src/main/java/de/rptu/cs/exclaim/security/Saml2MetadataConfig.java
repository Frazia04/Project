package de.rptu.cs.exclaim.security;

import de.rptu.cs.exclaim.ExclaimProperties;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.saml2.metadata.AttributeConsumingService;
import org.opensaml.saml.saml2.metadata.ContactPerson;
import org.opensaml.saml.saml2.metadata.EmailAddress;
import org.opensaml.saml.saml2.metadata.RequestedAttribute;
import org.opensaml.saml.saml2.metadata.RoleDescriptor;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.opensaml.saml.saml2.metadata.ServiceDescription;
import org.opensaml.saml.saml2.metadata.ServiceName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.provider.service.metadata.OpenSamlMetadataResolver;
import org.springframework.security.saml2.provider.service.metadata.Saml2MetadataResolver;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.function.Consumer;

/**
 * Configure the SAML metadata of our Service Provider.
 */
@Configuration(proxyBeanMethods = false)
@Slf4j
public class Saml2MetadataConfig {
    @Bean
    public Saml2MetadataResolver saml2MetadataResolver(ExclaimProperties exclaimProperties) {
        OpenSamlMetadataResolver metadataResolver = new OpenSamlMetadataResolver();
        metadataResolver.setEntityDescriptorCustomizer(parameters -> {
            for (RoleDescriptor roleDescriptor : parameters.getEntityDescriptor().getRoleDescriptors(SPSSODescriptor.DEFAULT_ELEMENT_NAME)) {
                if (roleDescriptor instanceof SPSSODescriptor spssoDescriptor) {
                    spssoDescriptor.getAttributeConsumingServices().add(
                        build(AttributeConsumingService.DEFAULT_ELEMENT_NAME, AttributeConsumingService.class, acs -> {
                            acs.setIndex(1);
                            acs.getNames().addAll(List.of(
                                buildServiceName("ExClaim", "de"),
                                buildServiceName("ExClaim", "en")
                            ));
                            acs.getDescriptions().addAll(List.of(
                                buildServiceDescription("Übungsabgabesystem der AG Softwaretechnik", "de"),
                                buildServiceDescription("Exercise submission system of the AG Softwaretechnik", "en")
                            ));
                            acs.getRequestedAttributes().addAll(List.of(
                                buildRequestedAttribute(Saml2AuthenticationConfig.SAMLID_ATTRIBUTE, true),
                                buildRequestedAttribute(Saml2AuthenticationConfig.FIRTNAME_ATTRIBUTE, true),
                                buildRequestedAttribute(Saml2AuthenticationConfig.LASTNAME_ATTRIBUTE, true),
                                buildRequestedAttribute(Saml2AuthenticationConfig.EMAIL_ATTRIBUTE, true),
                                buildRequestedAttribute(Saml2AuthenticationConfig.STUDENT_ID_ATTRIBUTE, false)
                            ));
                        })
                    );
                    spssoDescriptor.getContactPersons().add(build(ContactPerson.DEFAULT_ELEMENT_NAME, ContactPerson.class, contactPerson ->
                        contactPerson.getEmailAddresses().add(build(EmailAddress.DEFAULT_ELEMENT_NAME, EmailAddress.class, emailAddress ->
                            emailAddress.setURI(exclaimProperties.getAdminContact())
                        ))
                    ));
                }
            }
        });
        return metadataResolver;
    }

    private static <T> T build(QName elementName, Class<T> elementClass, Consumer<T> customizer) {
        T element = elementClass.cast(XMLObjectSupport.buildXMLObject(elementName));
        customizer.accept(element);
        return element;
    }

    private static ServiceName buildServiceName(String name, String language) {
        return build(ServiceName.DEFAULT_ELEMENT_NAME, ServiceName.class, serviceName -> {
            serviceName.setValue(name);
            serviceName.setXMLLang(language);
        });
    }

    private static ServiceDescription buildServiceDescription(String description, String language) {
        return build(ServiceDescription.DEFAULT_ELEMENT_NAME, ServiceDescription.class, serviceDescription -> {
            serviceDescription.setValue(description);
            serviceDescription.setXMLLang(language);
        });
    }

    private static RequestedAttribute buildRequestedAttribute(String name, boolean isRequired) {
        return build(RequestedAttribute.DEFAULT_ELEMENT_NAME, RequestedAttribute.class, requestedAttribute -> {
            requestedAttribute.setName(name);
            requestedAttribute.setIsRequired(isRequired);
        });
    }
}
