package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.entity.Department;
import com.careq.doctor.entity.DoctorCatalogEntry;
import com.careq.doctor.exception.DoctorCatalogNotFoundException;
import com.careq.doctor.exception.DuplicateDoctorCatalogEntryException;
import com.careq.doctor.repository.DepartmentRepository;
import com.careq.doctor.repository.DoctorCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DoctorCatalogServiceImpl implements DoctorCatalogService {

    private final DoctorCatalogRepository doctorCatalogRepository;
    private final DepartmentRepository departmentRepository;

    public DoctorCatalogServiceImpl(DoctorCatalogRepository doctorCatalogRepository,
                                     DepartmentRepository departmentRepository) {
        this.doctorCatalogRepository = doctorCatalogRepository;
        this.departmentRepository = departmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DoctorCatalogResponseDto> getAllDoctors(Long departmentId, String specialization) {
        List<DoctorCatalogEntry> entries;

        if (departmentId != null && specialization != null && !specialization.isBlank()) {
            entries = doctorCatalogRepository
                    .findByDepartmentIdAndSpecializationContainingIgnoreCase(departmentId, specialization);
        } else if (departmentId != null) {
            entries = doctorCatalogRepository.findByDepartmentId(departmentId);
        } else if (specialization != null && !specialization.isBlank()) {
            entries = doctorCatalogRepository.findBySpecializationContainingIgnoreCase(specialization);
        } else {
            entries = doctorCatalogRepository.findAll();
        }

        return entries.stream()
                .map(entry -> DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public DoctorCatalogResponseDto getDoctorById(Long id) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findById(id)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "Doctor catalog entry not found with id: " + id));
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    public DoctorCatalogResponseDto createDoctor(DoctorCatalogRequestDto request) {
        if (doctorCatalogRepository.existsByUserId(request.getUserId())) {
            throw new DuplicateDoctorCatalogEntryException(
                    "Doctor catalog entry already exists for userId: " + request.getUserId());
        }

        // Validate department exists
        if (!departmentRepository.existsById(request.getDepartmentId())) {
            throw new IllegalArgumentException(
                    "Department not found with id: " + request.getDepartmentId());
        }

        DoctorCatalogEntry entry = new DoctorCatalogEntry();
        entry.setName(request.getName().trim());
        entry.setUserId(request.getUserId());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setSpecialization(request.getSpecialization().trim());
        entry.setQualification(request.getQualification().trim());
        entry.setExperienceYears(request.getExperienceYears());
        entry.setConsultationFee(request.getConsultationFee());
        entry.setAvgConsultationTimeMinutes(request.getAvgConsultationTimeMinutes());
        entry.setIsAvailable(true);

        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    public DoctorCatalogResponseDto updateDoctor(Long id, DoctorCatalogRequestDto request) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findById(id)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "Doctor catalog entry not found with id: " + id));

        // Validate department exists
        if (!departmentRepository.existsById(request.getDepartmentId())) {
            throw new IllegalArgumentException(
                    "Department not found with id: " + request.getDepartmentId());
        }

        entry.setName(request.getName().trim());
        entry.setDepartmentId(request.getDepartmentId());
        entry.setSpecialization(request.getSpecialization().trim());
        entry.setQualification(request.getQualification().trim());
        entry.setExperienceYears(request.getExperienceYears());
        entry.setConsultationFee(request.getConsultationFee());
        entry.setAvgConsultationTimeMinutes(request.getAvgConsultationTimeMinutes());

        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    @Override
    @Transactional
    public void deleteDoctor(Long id) {
        if (!doctorCatalogRepository.existsById(id)) {
            throw new DoctorCatalogNotFoundException(
                    "Doctor catalog entry not found with id: " + id);
        }
        doctorCatalogRepository.deleteById(id);
    }

    @Override
    @Transactional
    public DoctorCatalogResponseDto toggleAvailability(String userId, AvailabilityRequestDto request) {
        DoctorCatalogEntry entry = doctorCatalogRepository.findByUserId(userId)
                .orElseThrow(() -> new DoctorCatalogNotFoundException(
                        "No doctor catalog entry found for userId: " + userId));

        entry.setIsAvailable(request.getIsAvailable());
        entry = doctorCatalogRepository.save(entry);
        return DoctorCatalogResponseDto.fromEntity(entry, getDepartmentName(entry.getDepartmentId()));
    }

    private String getDepartmentName(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .map(Department::getName)
                .orElse("Unknown");
    }
}
