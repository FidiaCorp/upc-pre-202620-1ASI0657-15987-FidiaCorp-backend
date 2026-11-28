package com.fidiacorp.credicasa.identity.infrastructure.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/** UserDetails de Spring Security que conserva correo y nombre visible para emitirlos como claims del JWT. */
public class AccountPrincipal extends User {
    private final String email;
    private final String displayName;

    public AccountPrincipal(String accountId, String passwordHash, Collection<? extends GrantedAuthority> authorities,
                            String email, String displayName) {
        super(accountId, passwordHash, authorities);
        this.email = email;
        this.displayName = displayName;
    }

    public String email() {
        return email;
    }

    public String displayName() {
        return displayName;
    }
}
