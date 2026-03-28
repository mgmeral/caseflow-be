package com.caseflow.auth;

import com.caseflow.identity.domain.User;
import com.caseflow.identity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaseFlowUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CaseFlowUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads user by userId extracted from the JWT — used by JwtAuthenticationFilter.
     * Role (+ EAGER permissions) and groups are JOIN FETCHed in one query.
     */
    @Transactional(readOnly = true)
    public CaseFlowUserDetails loadById(Long userId) {
        User user = userRepository.findByIdWithRoleAndGroups(userId)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + userId));
        return new CaseFlowUserDetails(user);
    }

    /**
     * Spring Security AuthenticationManager entry point — used at login time.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsernameWithRoleAndGroups(username)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        return new CaseFlowUserDetails(user);
    }
}
