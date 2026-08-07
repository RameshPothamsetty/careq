package com.careq.doctor.config;

import com.careq.doctor.entity.Department;
import com.careq.doctor.repository.DepartmentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Day 10 follow-up — BUG-2b: {@link DataSeeder} must HEAL missing departments
 * (create only the missing defaults) instead of skipping whenever the table is
 * non-empty, so a partially deleted table (e.g. Cardiology missing after a
 * manual cleanup) self-heals on restart.
 */
@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @InjectMocks
    private DataSeeder dataSeeder;

    @Test
    void run_EmptyTable_CreatesAllTenDefaultDepartments() {
        given(departmentRepository.existsByName(anyString())).willReturn(false);

        dataSeeder.run();

        verify(departmentRepository, times(10)).save(any(Department.class));
    }

    @Test
    void run_PartiallyDeletedTable_HealsOnlyTheMissingDepartments() {
        // The observed BUG-2 case: Cardiology was deleted from a non-empty
        // table. It now exists again — the other 9 are missing.
        given(departmentRepository.existsByName(anyString()))
                .willAnswer(invocation -> "Cardiology".equals(invocation.getArgument(0)));

        dataSeeder.run();

        // The 9 missing defaults are created; the existing one is left untouched.
        verify(departmentRepository, times(9)).save(any(Department.class));
        verify(departmentRepository, never()).save(argThat(
                dept -> "Cardiology".equals(dept.getName())));
    }
}
