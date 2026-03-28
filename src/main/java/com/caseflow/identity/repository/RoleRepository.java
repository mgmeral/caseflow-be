package com.caseflow.identity.repository;

import com.caseflow.identity.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    List<Role> findByIsActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);

    /**
     * Count active users who have both USER_MANAGE and ROLE_MANAGE through their active role,
     * excluding users whose role is the one with the given id.
     * Used to validate admin-capability preservation.
     */
    @Query("""
           SELECT COUNT(u) FROM User u
           WHERE u.isActive = true
             AND u.role.isActive = true
             AND u.role.id <> :excludeRoleId
             AND 'USER_MANAGE' MEMBER OF u.role.permissions
             AND 'ROLE_MANAGE' MEMBER OF u.role.permissions
           """)
    long countActiveAdminUsersExcludingRole(@Param("excludeRoleId") Long excludeRoleId);

    /**
     * Count all active users who have both USER_MANAGE and ROLE_MANAGE
     * through any active role. Used for the initial safety check.
     */
    @Query("""
           SELECT COUNT(u) FROM User u
           WHERE u.isActive = true
             AND u.role.isActive = true
             AND 'USER_MANAGE' MEMBER OF u.role.permissions
             AND 'ROLE_MANAGE' MEMBER OF u.role.permissions
           """)
    long countAllActiveAdminUsers();
}
