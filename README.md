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

Default credentials: `admin / admin123` · `agent / agent123` · `viewer / viewer123`

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

See [docs/api-notes.md](docs/api-notes.md) for endpoint reference, auth model, and error shapes.
