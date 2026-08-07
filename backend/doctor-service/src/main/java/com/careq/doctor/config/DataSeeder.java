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
        // BUG-2 fix: heal missing departments instead of skipping whenever the
        // table is non-empty — a partially deleted table (e.g. Cardiology
        // missing after a manual cleanup or a failed migration) now self-heals
        // on restart, while existing departments are left untouched.
        int created = seedDepartments();
        if (created > 0) {
            System.out.println("✅ Seeded " + created + " missing default hospital departments");
        }
    }

    private int seedDepartments() {
        int created = 0;
        created += createDepartmentIfMissing("Cardiology",
                "Diagnosis and treatment of heart and blood vessel disorders. " +
                "Includes Interventional Cardiology, Cardiac Electrophysiology, " +
                "and Pediatric Cardiology.");

        created += createDepartmentIfMissing("Neurology",
                "Diagnosis and treatment of brain, spinal cord, and nerve disorders. " +
                "Includes Stroke Neurology, Epilepsy Management, " +
                "and Neuromuscular Medicine.");

        created += createDepartmentIfMissing("Orthopedics",
                "Treatment of musculoskeletal system injuries and conditions. " +
                "Includes Joint Replacement Surgery, Sports Medicine, " +
                "and Spine Surgery.");

        created += createDepartmentIfMissing("Pediatrics",
                "Medical care for infants, children, and adolescents. " +
                "Includes General Pediatrics, Neonatology, " +
                "and Adolescent Medicine.");

        created += createDepartmentIfMissing("Dermatology",
                "Diagnosis and treatment of skin, hair, and nail conditions. " +
                "Includes Medical Dermatology, Cosmetic Dermatology, " +
                "and Pediatric Dermatology.");

        created += createDepartmentIfMissing("Ophthalmology",
                "Eye care including medical and surgical treatments. " +
                "Includes Cataract Surgery, Vitreo-Retinal Surgery, " +
                "and Glaucoma Management.");

        created += createDepartmentIfMissing("ENT (Otorhinolaryngology)",
                "Treatment of ear, nose, throat, head, and neck disorders. " +
                "Includes Head & Neck Surgery, Otology, " +
                "and Rhinology & Sinus Surgery.");

        created += createDepartmentIfMissing("Gastroenterology",
                "Diagnosis and treatment of digestive system disorders. " +
                "Includes Hepatology, Inflammatory Bowel Disease, " +
                "and Pancreatic & Biliary Disorders.");

        created += createDepartmentIfMissing("Pulmonology",
                "Diagnosis and treatment of respiratory system conditions. " +
                "Includes Respiratory Medicine, Sleep Medicine, " +
                "and Critical Care Pulmonology.");

        created += createDepartmentIfMissing("Nephrology",
                "Diagnosis and treatment of kidney diseases. " +
                "Includes Dialysis Services, Kidney Transplant Medicine, " +
                "and Hypertension & Fluid Management.");
        return created;
    }

    private int createDepartmentIfMissing(String name, String description) {
        if (departmentRepository.existsByName(name)) {
            return 0;
        }
        departmentRepository.save(new Department(name, description));
        return 1;
    }
}
