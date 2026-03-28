package com.caseflow.auth;

import com.caseflow.identity.domain.Group;
import com.caseflow.identity.domain.Permission;
import com.caseflow.identity.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Spring Security principal for CaseFlow.
 *
 * Authorities emitted:
 *   ROLE_{roleCode}  — e.g. ROLE_ADMIN  (kept for Spring Security role checks only)
 *   PERM_{permCode}  — e.g. PERM_TICKET_READ  (use these for business authorization)
 *
 * Authorization decisions must rely on PERM_* authorities, not ROLE_* names.
 */
public class CaseFlowUserDetails implements UserDetails {

    private final User user;

    public CaseFlowUserDetails(User user) {
        this.user = user;
    }

    public Long getUserId() { return user.getId(); }
    public String getEmail() { return user.getEmail(); }
    public String getFullName() { return user.getFullName(); }

    public Long getRoleId() {
        return user.getRole() != null ? user.getRole().getId() : null;
    }

    public String getRoleCode() {
        return user.getRole() != null ? user.getRole().getCode() : null;
    }

    public String getRoleName() {
        return user.getRole() != null ? user.getRole().getName() : null;
    }

    public String getTicketScope() {
        return user.getRole() != null && user.getRole().getTicketScope() != null
                ? user.getRole().getTicketScope().name()
                : null;
    }

    public Set<Permission> getPermissions() {
        return user.getRole() != null ? user.getRole().getPermissions() : Set.of();
    }

    public List<Long> getGroupIds() {
        return user.getGroups().stream().map(Group::getId).toList();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().getCode()));
            user.getRole().getPermissions().forEach(p ->
                    authorities.add(new SimpleGrantedAuthority("PERM_" + p.name())));
        }
        return authorities;
    }

    @Override public String getPassword() { return user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return Boolean.TRUE.equals(user.getIsActive()); }
}
