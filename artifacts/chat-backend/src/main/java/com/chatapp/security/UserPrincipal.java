package com.chatapp.security;

import com.chatapp.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security's UserDetails implementation wrapping our User entity.
 *
 * This adapter bridges our domain model with Spring Security's authentication system.
 * The UserPrincipal is stored in the SecurityContext during authenticated requests.
 *
 * Note: We use a simple ROLE_USER for now. Extend with ROLE_ADMIN, ROLE_MODERATOR
 * as needed using Spring Security's role-based access control.
 */
@AllArgsConstructor
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String email;
    private final String password;

    /**
     * Build a UserPrincipal from a User entity.
     * Called by UserDetailsServiceImpl after loading the user from DB.
     */
    public static UserPrincipal create(User user) {
        return new UserPrincipal(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getPassword()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Single role for now. Extend for admin/moderator functionality.
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
