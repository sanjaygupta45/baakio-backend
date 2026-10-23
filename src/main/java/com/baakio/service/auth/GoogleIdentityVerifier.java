package com.baakio.service.auth;

public interface GoogleIdentityVerifier {
    VerifiedGoogleIdentity verify(String idToken);

    record VerifiedGoogleIdentity(String subject, String email, String displayName) {}
}
