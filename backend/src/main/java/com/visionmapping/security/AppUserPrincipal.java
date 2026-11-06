package com.visionmapping.security;

import com.visionmapping.entity.AppUser;
import com.visionmapping.entity.enums.UserStatus;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@Getter
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String fullName;
    private final String email;
    private final String password;
    private final String role;
    private final boolean enabled;

    public AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.fullName = user.getFullName();
        this.email = user.getEmail();
        this.password = user.getPasswordHash();
        this.role = user.getRole().name();
        // V10 (SEC-01-05): only an Active account may sign in or use a token.
        // Spring checks this at sign-in; JwtService.isValid checks it on every
        // request, so disabling an account also ends sessions already open.
        this.enabled = user.getStatus() == UserStatus.ACTIVE;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
