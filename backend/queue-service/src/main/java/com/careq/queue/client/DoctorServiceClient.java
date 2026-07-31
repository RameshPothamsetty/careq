package com.careq.queue.client;

import com.careq.queue.dto.DoctorCatalogResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * Declarative Feign client for doctor-service (registered via Eureka).
 *
 * queue-service reads a doctor's avgConsultationTimeMinutes and isAvailable
 * live from doctor-service instead of duplicating them into its own tables —
 * a single source of truth for consultation data.
 *
 * doctor-service has no Spring Security filter of its own (auth is enforced
 * at the API Gateway), so direct service-to-service calls are allowed.
 */
@FeignClient(name = "doctor-service")
public interface DoctorServiceClient {

    @GetMapping("/api/doctors/{id}")
    DoctorCatalogResponseDto getDoctorById(@PathVariable("id") Long id);

    @GetMapping("/api/doctors")
    List<DoctorCatalogResponseDto> getAllDoctors();
}
