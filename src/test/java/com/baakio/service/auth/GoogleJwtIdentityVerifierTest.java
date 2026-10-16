package com.baakio.service.auth;

import com.baakio.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleJwtIdentityVerifierTest {
    private static final String CLIENT_ID = "baakio-client";

    @Test
    void acceptsVerifiedGoogleIdentityForThisClient() {
        var identity = GoogleJwtIdentityVerifier.validate(token(List.of(CLIENT_ID), null), CLIENT_ID);
        assertEquals("google-subject", identity.subject());
        assertEquals("owner@gmail.com", identity.email());
    }

    @Test
    void rejectsTokenPresentedByAnotherClient() {
        ApiException error = assertThrows(ApiException.class,
                () -> GoogleJwtIdentityVerifier.validate(token(List.of(CLIENT_ID), "another-client"), CLIENT_ID));
        assertEquals("INVALID_GOOGLE_TOKEN", error.code());
    }

    @Test
    void rejectsMultipleAudiencesWithoutAuthorizedParty() {
        ApiException error = assertThrows(ApiException.class,
                () -> GoogleJwtIdentityVerifier.validate(token(List.of(CLIENT_ID, "another-client"), null), CLIENT_ID));
        assertEquals("INVALID_GOOGLE_TOKEN", error.code());
    }

    private Jwt token(List<String> audience, String authorizedParty) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .subject("google-subject")
                .audience(audience)
                .claim("email", "Owner@Gmail.com")
                .claim("email_verified", true)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (authorizedParty != null) builder.claim("azp", authorizedParty);
        return builder.build();
    }
}
