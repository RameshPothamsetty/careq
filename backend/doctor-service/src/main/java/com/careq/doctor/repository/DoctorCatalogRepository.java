package com.careq.doctor.repository;

import com.careq.doctor.entity.DoctorCatalogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DoctorCatalogRepository extends JpaRepository<DoctorCatalogEntry, Long> {

    Optional<DoctorCatalogEntry> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * Single filtered + paginated search (Day 7a). Every filter is optional:
     * a null/blank value leaves that dimension unconstrained. {@code search}
     * matches the doctor's display name OR specialization (case-insensitive
     * substring). Sorting is applied through the {@link Pageable}.
     */
    @Query("SELECT e FROM DoctorCatalogEntry e WHERE "
            + "(:departmentId IS NULL OR e.departmentId = :departmentId) "
            + "AND (:specialization IS NULL OR :specialization = '' "
            + "     OR LOWER(e.specialization) LIKE LOWER(CONCAT('%', :specialization, '%'))) "
            + "AND (:search IS NULL OR :search = '' "
            + "     OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%')) "
            + "     OR LOWER(e.specialization) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<DoctorCatalogEntry> search(@Param("departmentId") Long departmentId,
                                    @Param("specialization") String specialization,
                                    @Param("search") String search,
                                    Pageable pageable);
}
