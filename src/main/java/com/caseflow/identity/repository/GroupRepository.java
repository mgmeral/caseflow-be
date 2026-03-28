package com.caseflow.identity.repository;

import com.caseflow.identity.domain.Group;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Optional<Group> findByName(String name);

    boolean existsByName(String name);

    /**
     * Fetches a Group with its users collection eagerly loaded via JOIN FETCH.
     * Use this whenever the result will be mapped to GroupResponse (memberCount/members).
     */
    @Query("SELECT g FROM Group g LEFT JOIN FETCH g.users LEFT JOIN FETCH g.groupType WHERE g.id = :id")
    Optional<Group> findByIdWithMembers(@Param("id") Long id);

    /**
     * All active groups with users and groupType pre-loaded — avoids LazyInitializationException
     * when the mapper reads memberCount/groupType fields from the detached entity.
     */
    @EntityGraph(attributePaths = {"users", "groupType"})
    List<Group> findByIsActiveTrue();
}
