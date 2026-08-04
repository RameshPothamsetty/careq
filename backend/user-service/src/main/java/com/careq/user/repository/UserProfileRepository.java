package com.careq.user.repository;

import com.careq.user.entity.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * Paginated admin listing with an optional search on display name OR email
     * (case-insensitive substring). A null/blank search returns all profiles.
     */
    @Query("SELECT p FROM UserProfile p WHERE "
            + "(:search IS NULL OR :search = '' "
            + " OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :search, '%')) "
            + " OR LOWER(p.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<UserProfile> search(@Param("search") String search, Pageable pageable);
}
