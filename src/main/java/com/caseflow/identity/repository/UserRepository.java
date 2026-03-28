package com.caseflow.identity.repository;

import com.caseflow.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    /**
     * Fetches all users with role eagerly — used by GET /api/users (list).
     * Groups are NOT fetched here; UserSummaryResponse does not need them.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role")
    List<User> findAllWithRole();

    /**
     * Fetches a single user with role and groups — used by GET /api/users/{id}.
     * Both are needed by UserResponse (roleId/roleCode/roleName + groupIds/groupNames).
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") Long id);

    /**
     * Loads a user with role and groups keyed by email — used by GET /api/users/by-email.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.email = :email")
    Optional<User> findByEmailWithRoleAndGroups(@Param("email") String email);

    /**
     * Loads a user with role and groups keyed by ID — used by the JWT filter and /auth/me.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.id = :id")
    Optional<User> findByIdWithRoleAndGroups(@Param("id") Long id);

    /**
     * Loads a user with role and groups keyed by username — used by the authentication manager
     * and GET /api/users/by-username.
     */
    @Query("SELECT u FROM User u JOIN FETCH u.role LEFT JOIN FETCH u.groups WHERE u.username = :username")
    Optional<User> findByUsernameWithRoleAndGroups(@Param("username") String username);
}
