package com.careq.doctor.service;

import com.careq.doctor.dto.DepartmentRequestDto;
import com.careq.doctor.dto.DepartmentResponseDto;
import com.careq.doctor.entity.Department;
import com.careq.doctor.exception.DepartmentNotFoundException;
import com.careq.doctor.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DepartmentServiceImpl} .
 */
@ExtendWith(MockitoExtension.class)
class DepartmentServiceImplTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DepartmentServiceImpl departmentService;

    private DepartmentRequestDto request(String name) {
        DepartmentRequestDto request = new DepartmentRequestDto();
        request.setName(name);
        request.setDescription("Heart and cardiovascular system");
        return request;
    }

    @Test
    void getAllDepartments_ShouldReturnList() {
        when(departmentRepository.findAll()).thenReturn(List.of(
                new Department("Cardiology", "Heart"),
                new Department("Neurology", "Brain")));

        List<DepartmentResponseDto> result = departmentService.getAllDepartments();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DepartmentResponseDto::getName)
                .containsExactly("Cardiology", "Neurology");
    }

    @Test
    void getDepartmentById_WhenExists_ShouldReturnDepartment() {
        Department department = new Department("Cardiology", "Heart");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        DepartmentResponseDto result = departmentService.getDepartmentById(1L);

        assertThat(result.getName()).isEqualTo("Cardiology");
        assertThat(result.getDescription()).isEqualTo("Heart");
    }

    @Test
    void getDepartmentById_WhenNotExists_ShouldThrowException() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.getDepartmentById(99L))
                .isInstanceOf(DepartmentNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createDepartment_WithUniqueName_ShouldCreate() {
        when(departmentRepository.existsByName("Cardiology")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentResponseDto result = departmentService.createDepartment(request("  Cardiology  "));

        assertThat(result.getName()).isEqualTo("Cardiology"); // trimmed
        assertThat(result.getDescription()).isEqualTo("Heart and cardiovascular system");
        assertThat(result.getIsActive()).isTrue();
        verify(departmentRepository).existsByName("Cardiology");
    }

    @Test
    void createDepartment_WithDuplicateName_ShouldThrowException() {
        when(departmentRepository.existsByName("Cardiology")).thenReturn(true);

        assertThatThrownBy(() -> departmentService.createDepartment(request("Cardiology")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(departmentRepository, never()).save(any());
    }

    @Test
    void updateDepartment_WhenExists_ShouldUpdate() {
        Department department = new Department("Cardiology", "Heart");
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(departmentRepository.existsByName("Neurology")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenAnswer(inv -> inv.getArgument(0));

        DepartmentResponseDto result = departmentService.updateDepartment(1L, request("Neurology"));

        assertThat(result.getName()).isEqualTo("Neurology");
        assertThat(result.getDescription()).isEqualTo("Heart and cardiovascular system");
    }

    @Test
    void updateDepartment_WhenNotExists_ShouldThrowException() {
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> departmentService.updateDepartment(99L, request("Cardiology")))
                .isInstanceOf(DepartmentNotFoundException.class);
    }

    @Test
    void deleteDepartment_WhenExists_ShouldDelete() {
        when(departmentRepository.existsById(1L)).thenReturn(true);

        departmentService.deleteDepartment(1L);

        verify(departmentRepository).deleteById(1L);
    }

    @Test
    void deleteDepartment_WhenNotExists_ShouldThrowException() {
        when(departmentRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> departmentService.deleteDepartment(99L))
                .isInstanceOf(DepartmentNotFoundException.class);

        verify(departmentRepository, never()).deleteById(any());
    }
}
