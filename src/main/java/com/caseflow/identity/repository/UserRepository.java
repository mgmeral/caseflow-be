package com.caseflow.identity.repository;

import com.caseflow.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Loads a user with role (+ permissions via EAGER @ElementCollection) and groups
     * in a single query pass — used by the JWT filter and /auth/me to build CaseFlowUserDetails.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.id = :id")
    Optional<User> findByIdWithRoleAndGroups(@Param("id") Long id);

    /**
     * Same as above but keyed by username — used by the authentication manager.
     */
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.username = :username")
    Optional<User> findByUsernameWithRoleAndGroups(@Param("username") String username);
}
