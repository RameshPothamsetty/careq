package com.careq.doctor.service;

import com.careq.doctor.dto.AvailabilityRequestDto;
import com.careq.doctor.dto.DoctorCatalogRequestDto;
import com.careq.doctor.dto.DoctorCatalogResponseDto;
import com.careq.doctor.entity.Department;
import com.careq.doctor.entity.DoctorCatalogEntry;
import com.careq.doctor.exception.DepartmentNotFoundException;
import com.careq.doctor.exception.DoctorCatalogNotFoundException;
import com.careq.doctor.exception.DuplicateDoctorCatalogEntryException;
import com.careq.doctor.repository.DepartmentRepository;
import com.careq.doctor.repository.DoctorCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DoctorCatalogServiceImpl} .
 */
@ExtendWith(MockitoExtension.class)
class DoctorCatalogServiceImplTest {

    private static final Long DEPT_ID = 1L;
    private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440001";

    @Mock
    private DoctorCatalogRepository doctorCatalogRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private DoctorCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DoctorCatalogServiceImpl(doctorCatalogRepository, departmentRepository, 100);
    }

    private DoctorCatalogEntry entry(String userId, String name, Long departmentId, String specialization) {
        DoctorCatalogEntry entry = new DoctorCatalogEntry();
        entry.setName(name);
        entry.setUserId(userId);
        entry.setDepartmentId(departmentId);
        entry.setSpecialization(specialization);
        entry.setQualification("MD");
        entry.setExperienceYears(10);
        entry.setConsultationFee(new BigDecimal("500.00"));
        entry.setAvgConsultationTimeMinutes(15);
        entry.setIsAvailable(true);
        return entry;
    }

    private DoctorCatalogRequestDto request() {
        DoctorCatalogRequestDto request = new DoctorCatalogRequestDto();
        request.setName("  Dr. Arjun Sharma  ");
        request.setUserId(USER_ID);
        request.setDepartmentId(DEPT_ID);
        request.setSpecialization("  Interventional Cardiology  ");
        request.setQualification("MD, DM Cardiology");
        request.setExperienceYears(12);
        request.setConsultationFee(new BigDecimal("500.00"));
        request.setAvgConsultationTimeMinutes(15);
        return request;
    }

    private void stubDepartmentName() {
        when(departmentRepository.findById(DEPT_ID))
                .thenReturn(Optional.of(new Department("Cardiology", "Heart")));
    }

    private Page<DoctorCatalogEntry> page(DoctorCatalogEntry... entries) {
        return new PageImpl<>(List.of(entries));
    }

    @Test
    void getAllDoctors_WithoutFilters_ShouldReturnAll() {
        when(doctorCatalogRepository.search(any(), any(), any(), any()))
                .thenReturn(page(entry(USER_ID, "Dr. Arjun Sharma", DEPT_ID, "Cardiology")));
        stubDepartmentName();

        Page<DoctorCatalogResponseDto> result =
                service.getAllDoctors(null, null, null, 0, 20, "name", "asc");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDepartmentName()).isEqualTo("Cardiology");
        assertThat(result.getContent().get(0).getName()).isEqualTo("Dr. Arjun Sharma");
    }

    @Test
    void getAllDoctors_WithDepartmentFilter_ShouldReturnFiltered() {
        when(doctorCatalogRepository.search(any(), any(), any(), any())).thenReturn(page());

        service.getAllDoctors(DEPT_ID, null, null, 0, 20, "name", "asc");

        ArgumentCaptor<Long> deptCaptor = ArgumentCaptor.forClass(Long.class);
        verify(doctorCatalogRepository).search(deptCaptor.capture(), any(), any(), any());
        assertThat(deptCaptor.getValue()).isEqualTo(DEPT_ID);
    }

    @Test
    void getAllDoctors_WithSpecializationFilter_ShouldReturnFiltered() {
        when(doctorCatalogRepository.search(any(), any(), any(), any())).thenReturn(page());

        service.getAllDoctors(null, "  cardiology  ", null, 0, 20, "name", "asc");

        ArgumentCaptor<String> specCaptor = ArgumentCaptor.forClass(String.class);
        verify(doctorCatalogRepository).search(any(), specCaptor.capture(), any(), any());
        assertThat(specCaptor.getValue()).isEqualTo("cardiology"); // trimmed
    }

    @Test
    void getAllDoctors_WithBothFilters_ShouldReturnFiltered() {
        when(doctorCatalogRepository.search(any(), any(), any(), any())).thenReturn(page());

        service.getAllDoctors(DEPT_ID, "cardiology", "  sharma  ", 0, 20, "name", "asc");

        ArgumentCaptor<Long> deptCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<String> specCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> searchCaptor = ArgumentCaptor.forClass(String.class);
        verify(doctorCatalogRepository).search(deptCaptor.capture(), specCaptor.capture(), searchCaptor.capture(), any());
        assertThat(deptCaptor.getValue()).isEqualTo(DEPT_ID);
        assertThat(specCaptor.getValue()).isEqualTo("cardiology");
        assertThat(searchCaptor.getValue()).isEqualTo("sharma");
    }

    @Test
    void getAllDoctors_ClampsPageSizeAndSorts() {
        when(doctorCatalogRepository.search(any(), any(), any(), any())).thenReturn(page());

        // Requested size 500 -> clamped to maxPageSize (100); page -2 -> 0.
        service.getAllDoctors(null, null, null, -2, 500, "consultationFee", "desc");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(doctorCatalogRepository).search(any(), any(), any(), pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort().getOrderFor("consultationFee").getDirection().name()).isEqualTo("DESC");
    }

    @Test
    void getDoctorById_WhenExists_ShouldReturnEntry() {
        when(doctorCatalogRepository.findById(1L))
                .thenReturn(Optional.of(entry(USER_ID, "Dr. Arjun Sharma", DEPT_ID, "Cardiology")));
        stubDepartmentName();

        DoctorCatalogResponseDto result = service.getDoctorById(1L);

        assertThat(result.getName()).isEqualTo("Dr. Arjun Sharma");
        assertThat(result.getDepartmentName()).isEqualTo("Cardiology");
        assertThat(result.getIsAvailable()).isTrue();
    }

    @Test
    void getDoctorById_WhenNotExists_ShouldThrowException() {
        when(doctorCatalogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDoctorById(99L))
                .isInstanceOf(DoctorCatalogNotFoundException.class);
    }

    @Test
    void createDoctor_WithUniqueUserId_ShouldCreate() {
        when(doctorCatalogRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(departmentRepository.existsById(DEPT_ID)).thenReturn(true);
        when(doctorCatalogRepository.save(any(DoctorCatalogEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDepartmentName();

        DoctorCatalogResponseDto result = service.createDoctor(request());

        assertThat(result.getName()).isEqualTo("Dr. Arjun Sharma"); // trimmed
        assertThat(result.getSpecialization()).isEqualTo("Interventional Cardiology");
        assertThat(result.getIsAvailable()).isTrue(); // defaults to available
        assertThat(result.getDepartmentName()).isEqualTo("Cardiology");
    }

    @Test
    void createDoctor_WithDuplicateUserId_ShouldThrowException() {
        when(doctorCatalogRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createDoctor(request()))
                .isInstanceOf(DuplicateDoctorCatalogEntryException.class)
                .hasMessageContaining(USER_ID);

        verify(doctorCatalogRepository, never()).save(any());
    }

    @Test
    void createDoctor_WithInvalidDepartment_ShouldThrowException() {
        // Note: the real implementation throws DepartmentNotFoundException
        // (not IllegalArgumentException as originally planned in docs/09_TESTING.md).
        when(doctorCatalogRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(departmentRepository.existsById(DEPT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createDoctor(request()))
                .isInstanceOf(DepartmentNotFoundException.class)
                .hasMessageContaining(String.valueOf(DEPT_ID));

        verify(doctorCatalogRepository, never()).save(any());
    }

    @Test
    void updateDoctor_WhenExists_ShouldUpdate() {
        when(doctorCatalogRepository.findById(1L))
                .thenReturn(Optional.of(entry(USER_ID, "Dr. Arjun Sharma", DEPT_ID, "Cardiology")));
        when(departmentRepository.existsById(DEPT_ID)).thenReturn(true);
        when(doctorCatalogRepository.save(any(DoctorCatalogEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDepartmentName();

        DoctorCatalogRequestDto request = request();
        request.setName("Dr. Arjun Sharma Jr.");
        request.setExperienceYears(15);

        DoctorCatalogResponseDto result = service.updateDoctor(1L, request);

        assertThat(result.getName()).isEqualTo("Dr. Arjun Sharma Jr.");
        assertThat(result.getExperienceYears()).isEqualTo(15);
        assertThat(result.getUserId()).isEqualTo(USER_ID);
    }

    @Test
    void deleteDoctor_WhenExists_ShouldDelete() {
        when(doctorCatalogRepository.existsById(1L)).thenReturn(true);

        service.deleteDoctor(1L);

        verify(doctorCatalogRepository).deleteById(1L);
    }

    @Test
    void deleteDoctor_WhenNotExists_ShouldThrowException() {
        when(doctorCatalogRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.deleteDoctor(99L))
                .isInstanceOf(DoctorCatalogNotFoundException.class);

        verify(doctorCatalogRepository, never()).deleteById(any());
    }

    @Test
    void toggleAvailability_WhenExists_ShouldToggle() {
        when(doctorCatalogRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(entry(USER_ID, "Dr. Arjun Sharma", DEPT_ID, "Cardiology")));
        when(doctorCatalogRepository.save(any(DoctorCatalogEntry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        stubDepartmentName();

        AvailabilityRequestDto request = new AvailabilityRequestDto();
        request.setIsAvailable(false);

        DoctorCatalogResponseDto result = service.toggleAvailability(USER_ID, request);

        assertThat(result.getIsAvailable()).isFalse();
        verify(doctorCatalogRepository).save(any(DoctorCatalogEntry.class));
    }

    @Test
    void toggleAvailability_WhenNotExists_ShouldThrowException() {
        when(doctorCatalogRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        AvailabilityRequestDto request = new AvailabilityRequestDto();
        request.setIsAvailable(true);

        assertThatThrownBy(() -> service.toggleAvailability(USER_ID, request))
                .isInstanceOf(DoctorCatalogNotFoundException.class);
    }

    @Test
    void getMyDoctor_WhenLinked_ShouldReturnCallersEntry() {
        when(doctorCatalogRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(entry(USER_ID, "Dr. Arjun Sharma", DEPT_ID, "Cardiology")));
        stubDepartmentName();

        DoctorCatalogResponseDto result = service.getMyDoctor(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getName()).isEqualTo("Dr. Arjun Sharma");
        assertThat(result.getDepartmentName()).isEqualTo("Cardiology");
    }

    @Test
    void getMyDoctor_WhenNotLinked_ShouldThrowException() {
        when(doctorCatalogRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyDoctor(USER_ID))
                .isInstanceOf(DoctorCatalogNotFoundException.class)
                .hasMessageContaining("admin");
    }
}
