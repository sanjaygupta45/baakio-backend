package com.baakio.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

public record AccountHolderPrincipal(
        String id,
        String businessId,
        String businessName,
        String displayName,
        String phoneNumber,
        String email,
        boolean active
) implements Serializable {

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ACCOUNT_HOLDER"));
    }
}
