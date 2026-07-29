# CareQ — Intelligent Patient Flow Platform

**Brand:** SmartOPD AI

An AI-powered OPD (Outpatient Department) operations platform that predicts patient wait times, triages patients by urgency using AI, and gives Patients, Doctors, and Admins a live, role-specific view of hospital queue operations.

---

## Project Status

| Day | Module | Status |
|-----|--------|--------|
| Day 1 | Project Scaffold | ✅ Complete |
| Day 2 | Authentication (JWT, Login, Signup) | ✅ Complete |
| **Day 3** | **User Module (Profiles, File Upload)** | **✅ Complete** |
| Day 4 | Doctor/Department Module | ⏳ Upcoming |
| Day 5+ | Queue, AI Features | 📅 Planned |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18 + TypeScript + Vite |
| Backend | Spring Boot 3.2 + Java 17 |
| Service Registry | Netflix Eureka |
| API Gateway | Spring Cloud Gateway |
| Database | MySQL 8 |
| Auth | Spring Security + JWT (HMAC-SHA256) |
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
│   ├── user-service/              # Profile management + file upload (port 8082)
│   ├── doctor-service/            # Doctor/Department catalog (port 8083)
│   └── queue-service/             # Queue & AI operations (port 8084)
├── frontend/                      # React SPA (port 3030)
└── uploads/                       # Profile picture uploads (auto-created)
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
| [09_TESTING.md](docs/09_TESTING.md) | Unit test plans for auth-service and user-service |
| [11_PROMPTS.md](docs/11_PROMPTS.md) | Archive of daily development prompts |

---

## How to Run

### Prerequisites

- Java 17+
- Node.js 18+
- MySQL 8+
- Maven 3.9+

### Backend (Start in Order)

```bash
# 1. Start MySQL and create the database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS careq_db;"

# 2. Start Eureka Server (Service Registry)
cd backend/eureka-server
mvn spring-boot:run

# 3. Start API Gateway (JWT validation, routing)
cd backend/api-gateway && mvn spring-boot:run

# 4. Start Auth Service (login, signup, JWT)
cd backend/auth-service && mvn spring-boot:run

# 5. Start User Service (profiles, file upload)
cd backend/user-service && mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The app will be available at **http://localhost:3030**.

---

## API Endpoints (Day 3)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/signup` | No | Create account |
| POST | `/api/auth/login` | No | Login, get JWT |
| GET | `/api/users/me` | Yes | View own profile (lazy-created) |
| PUT | `/api/users/me` | Yes | Update own profile |
| POST | `/api/users/me/profile-picture` | Yes | Upload profile picture |
| GET | `/api/users/{id}` | Admin | View any user's profile |
| GET | `/api/users/profile-pictures/{filename}` | No | Serve uploaded images |

---

## Git Branching Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Production — always deployable |
| `develop` | Daily integration — merge PRs here |
| `feature/*` | One branch per day's checklist items (e.g., `feature/user-module`) |

---

## License

This project is built for educational purposes as part of the TrainingMug ADF v1.0 framework.
