package com.baakio.service.auth;

import com.baakio.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class GoogleJwtIdentityVerifier implements GoogleIdentityVerifier {
    private final NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
            "https://www.googleapis.com/oauth2/v3/certs").build();
    private final String clientId;

    public GoogleJwtIdentityVerifier(@Value("${baakio.auth.google.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public VerifiedGoogleIdentity verify(String idToken) {
        if (clientId == null || clientId.isBlank()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "GOOGLE_AUTH_NOT_CONFIGURED", "Google sign-in is not configured.");
        try {
            Jwt jwt = decoder.decode(idToken);
            String issuer = jwt.getIssuer() == null ? "" : jwt.getIssuer().toString();
            String email = jwt.getClaimAsString("email");
            String subject = jwt.getSubject();
            if (!(issuer.equals("https://accounts.google.com") || issuer.equals("accounts.google.com"))
                    || !jwt.getAudience().contains(clientId)
                    || !Boolean.TRUE.equals(jwt.getClaim("email_verified"))
                    || email == null || email.isBlank() || subject == null || subject.isBlank()) {
                throw invalid();
            }
            return new VerifiedGoogleIdentity(subject, email.trim().toLowerCase(Locale.ROOT),
                    jwt.getClaimAsString("name"));
        } catch (JwtException exception) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_GOOGLE_TOKEN", "Google sign-in could not be verified.");
    }
}
