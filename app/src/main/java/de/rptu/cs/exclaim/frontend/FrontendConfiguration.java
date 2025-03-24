package de.rptu.cs.exclaim.frontend;

import de.rptu.cs.exclaim.api.FEConfiguration;
import de.rptu.cs.exclaim.security.Saml2RelyingPartyRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FrontendConfiguration {
    private final Saml2RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    public FEConfiguration getConfiguration() {
        return new FEConfiguration(
            relyingPartyRegistrationRepository.getRegistrationIds()
        );
    }
}
