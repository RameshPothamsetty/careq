package com.careq.queue.client;

import com.careq.queue.dto.DoctorCatalogPageDto;
import com.careq.queue.dto.DoctorCatalogResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

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

    /**
     * GET /api/doctors is paginated since Day 7a — the caller asks for a page
     * and reads {@code .content}. A single page with a large size is used for
     * the Admin overview because it needs every doctor in the catalog.
     */
    @GetMapping("/api/doctors")
    DoctorCatalogPageDto getAllDoctors(@RequestParam("page") int page,
                                       @RequestParam("size") int size);
}
