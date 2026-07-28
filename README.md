# CareQ — Intelligent Patient Flow Platform

**Brand:** SmartOPD AI

An AI-powered OPD (Outpatient Department) operations platform that predicts patient wait times, triages patients by urgency using AI, and gives Patients, Doctors, and Admins a live, role-specific view of hospital queue operations.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3.2 + Java 17 |
| Service Registry | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Database | MySQL 8 |
| Auth | Spring Security + JWT |
| Build | Maven |

---

## Project Structure

```
careq/
├── README.md
├── docs/                          # Requirements, architecture, DB schema
├── backend/
│   ├── pom.xml                    # Parent Maven POM
│   ├── eureka-server/             # Service registry (port 8761)
│   ├── api-gateway/               # API gateway (port 8080)
│   ├── auth-service/              # Auth & JWT (port 8081)
│   ├── user-service/              # Profile management (port 8082)
│   ├── doctor-service/            # Doctor/Department catalog (port 8083)
│   └── queue-service/             # Queue & AI operations (port 8084)
├── frontend/                      # React SPA (port 3000)
├── docker/                        # Docker configs (future)
├── postman/                       # API collections (future)
└── .github/workflows/             # CI/CD (future)
```

---

## Documentation

All project documentation is in the [docs/](docs/) folder:

| Document | Description |
|----------|-------------|
| [01_PROJECT_CONTEXT.md](docs/01_PROJECT_CONTEXT.md) | Tech stack, architecture, folder structure, git strategy |
| [02_REQUIREMENTS.md](docs/02_REQUIREMENTS.md) | SRS — features, user stories, non-functional requirements |
| [03_ARCHITECTURE.md](docs/03_ARCHITECTURE.md) | Architecture diagram, service responsibilities, auth flow |
| [04_DATABASE.md](docs/04_DATABASE.md) | ER diagram, MySQL schema, column design rationale |

---

## How to Run (Placeholder)

> **Note:** These steps will be completed as each service is built.

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+
- Maven 3.9+

### Backend

```bash
# 1. Start MySQL and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS careq_db;"

# 2. Start Eureka Server
cd backend/eureka-server
mvn spring-boot:run

# 3. Start remaining services (in separate terminals)
cd backend/api-gateway  && mvn spring-boot:run
cd backend/auth-service && mvn spring-boot:run
cd backend/user-service && mvn spring-boot:run
cd backend/doctor-service && mvn spring-boot:run
cd backend/queue-service && mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at `http://localhost:3000`.

---

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production — always deployable |
| `develop` | Daily integration — push every evening |
| `feature/*` | One branch per day's checklist items |

---

## License

This project is built for educational purposes as part of the TrainingMug ADF v1.0 framework.
