package com.careq.doctor.repository;

import com.careq.doctor.entity.DoctorCatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorCatalogRepository extends JpaRepository<DoctorCatalogEntry, Long> {

    Optional<DoctorCatalogEntry> findByUserId(String userId);

    boolean existsByUserId(String userId);

    List<DoctorCatalogEntry> findByDepartmentId(Long departmentId);

    List<DoctorCatalogEntry> findBySpecializationContainingIgnoreCase(String specialization);

    List<DoctorCatalogEntry> findByDepartmentIdAndSpecializationContainingIgnoreCase(
            Long departmentId, String specialization);
}
