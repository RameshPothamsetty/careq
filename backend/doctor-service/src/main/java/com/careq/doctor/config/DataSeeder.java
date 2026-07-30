package com.careq.doctor.config;

import com.careq.doctor.entity.Department;
import com.careq.doctor.repository.DepartmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DataSeeder implements CommandLineRunner {

    private final DepartmentRepository departmentRepository;

    public DataSeeder(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Only seed if no departments exist yet
        if (departmentRepository.count() > 0) {
            return;
        }

        seedDepartments();
        System.out.println("✅ Seeded 10 default hospital departments");
    }

    private void seedDepartments() {
        createDepartment("Cardiology",
                "Diagnosis and treatment of heart and blood vessel disorders. " +
                "Includes Interventional Cardiology, Cardiac Electrophysiology, " +
                "and Pediatric Cardiology.");

        createDepartment("Neurology",
                "Diagnosis and treatment of brain, spinal cord, and nerve disorders. " +
                "Includes Stroke Neurology, Epilepsy Management, " +
                "and Neuromuscular Medicine.");

        createDepartment("Orthopedics",
                "Treatment of musculoskeletal system injuries and conditions. " +
                "Includes Joint Replacement Surgery, Sports Medicine, " +
                "and Spine Surgery.");

        createDepartment("Pediatrics",
                "Medical care for infants, children, and adolescents. " +
                "Includes General Pediatrics, Neonatology, " +
                "and Adolescent Medicine.");

        createDepartment("Dermatology",
                "Diagnosis and treatment of skin, hair, and nail conditions. " +
                "Includes Medical Dermatology, Cosmetic Dermatology, " +
                "and Pediatric Dermatology.");

        createDepartment("Ophthalmology",
                "Eye care including medical and surgical treatments. " +
                "Includes Cataract Surgery, Vitreo-Retinal Surgery, " +
                "and Glaucoma Management.");

        createDepartment("ENT (Otorhinolaryngology)",
                "Treatment of ear, nose, throat, head, and neck disorders. " +
                "Includes Head & Neck Surgery, Otology, " +
                "and Rhinology & Sinus Surgery.");

        createDepartment("Gastroenterology",
                "Diagnosis and treatment of digestive system disorders. " +
                "Includes Hepatology, Inflammatory Bowel Disease, " +
                "and Pancreatic & Biliary Disorders.");

        createDepartment("Pulmonology",
                "Diagnosis and treatment of respiratory system conditions. " +
                "Includes Respiratory Medicine, Sleep Medicine, " +
                "and Critical Care Pulmonology.");

        createDepartment("Nephrology",
                "Diagnosis and treatment of kidney diseases. " +
                "Includes Dialysis Services, Kidney Transplant Medicine, " +
                "and Hypertension & Fluid Management.");
    }

    private void createDepartment(String name, String description) {
        Department dept = new Department(name, description);
        departmentRepository.save(dept);
    }
}
