# CaseFlow

![CI](https://github.com/your-org/caseflow/actions/workflows/ci.yml/badge.svg)

Ticket and mail-based case management system.

**Stack:** Java 21 · Spring Boot 3.3 · PostgreSQL · MongoDB · Flyway · MapStruct · Spring Security

---

## Local Run (Docker Compose)

```bash
cp .env.example .env          # optional — all values have defaults
docker compose up -d           # starts app + postgres + mongo
curl http://localhost:8080/actuator/health
```

Open Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

Seed credentials (dev profile): `alice / admin123` · `bob / agent123` · `carol / viewer123`

See [docs/deployment-local.md](docs/deployment-local.md) for the full guide.

MinIO console: [http://localhost:9001](http://localhost:9001) — default credentials `minioadmin / minioadmin123`

---

## Build (without Docker)

```bash
mvn package -DskipTests
java -jar target/caseflow-0.0.1-SNAPSHOT.jar
```

Requires local PostgreSQL on `localhost:5432` and MongoDB on `localhost:27017`.

---

## Seed Data

Start with `SPRING_PROFILES_ACTIVE=dev` to auto-load sample groups, users, customers and tickets:

```bash
SPRING_PROFILES_ACTIVE=dev docker compose up -d
```

---

## CI / CD

GitHub Actions pipeline — see [docs/ci-cd.md](docs/ci-cd.md) for full details.

- **Build & test:** runs on every push and PR (`mvn verify`, no external services needed)
- **Docker image:** built after tests pass; pushed to `ghcr.io` on `main`/`master` using `GITHUB_TOKEN`

---

## Kubernetes

See [docs/deployment-k8s.md](docs/deployment-k8s.md) for K8s manifests and deployment guide (`k8s/`).

---

## API Documentation

- [docs/frontend-contract.md](docs/frontend-contract.md) — compact contract for frontend developers (auth flow, all endpoints, response shapes, enums)
- [docs/api-endpoints.md](docs/api-endpoints.md) — full per-endpoint request/response examples
- [docs/api-models.ts](docs/api-models.ts) — TypeScript type definitions matching backend DTOs
- [docs/api-notes.md](docs/api-notes.md) — error codes, enum values, CORS, seed data reference
