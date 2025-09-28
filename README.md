# Flow — Workflow Backend

## Overview
Flow is a small Spring Boot backend to create, schedule and run simple workflows composed of nodes (HTTP, NOTIFY, CONDITION, DELAY). Workflows, runs and run logs are persisted to MySQL. Authentication uses JWT tokens and there is a simple role `ADMIN` for privileged actions (execute/schedule).

---

## Quick start (local)

Prerequisites:
- Java 17
- Maven
- MySQL running locally and a database `flowdb` created
- `gh` CLI (optional, for PR creation)

1. Configure DB (edit `src/main/resources/application.properties` as needed):
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/flowdb
spring.datasource.username=username
spring.datasource.password=password
Build:

bash
Copy code
mvn -U clean package -DskipTests
Run:

bash
Copy code
java -jar target/workflow-0.0.1-SNAPSHOT.jar
Default server: http://localhost:8080

Authentication
Login:

bash
Copy code
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"pass"}' | jq .
Response includes token. Use it for protected endpoints:

Use header Authorization: Bearer <token>

Important APIs
POST /auth/login — obtain JWT token (body: {username,password})

GET /workflow/all — list all workflows (masked secrets by default)

GET /workflow/{id} — get workflow detail (add ?reveal=true to reveal secrets if you are admin)

POST /workflow/{id}/execute — execute workflow (ADMIN only)

POST /workflow/{id}/schedule/enable — enable scheduling (ADMIN)

POST /workflow/{id}/schedule/disable — disable scheduling (ADMIN)

POST /webhook/{workflowId} — trigger workflow via webhook (async)

GET /workflow/runs/{runId} — get run details and logs

Examples:

List workflows (with token):

bash
Copy code
TOKEN="eyJ...<your token>..."
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/workflow/all | jq .
Execute workflow (ADMIN):

bash
Copy code
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/workflow/3/execute | jq .
Disable schedule:

bash
Copy code
curl -s -X POST -H "Authorization: Bearer $TOKEN" http://localhost:8080/workflow/3/schedule/disable
Swagger / OpenAPI
Swagger UI URL: http://localhost:8080/swagger-ui/index.html

OpenAPI JSON: http://localhost:8080/v3/api-docs

Troubleshooting:

If /v3/api-docs returns 404, check springdoc dependency version in pom.xml. The project uses springdoc-openapi-starter-webmvc-ui. This must be compatible with your Spring Boot / Spring Web versions; mismatches cause runtime NoSuchMethodError.

If you see NoSuchMethodError: ControllerAdviceBean.<init> in logs, it means springdoc version is incompatible with your Spring Web version (or there's a mixed classpath). Fix by aligning springdoc to a version that supports Spring 6.x / Boot 3.x (e.g., springdoc 2.5.x is usually compatible with Spring Boot 3; if you removed or changed starters, ensure springdoc artifacts are present in the dependency tree).

If you have a custom OpenApiConfig bean that references GroupedOpenApi from org.springdoc.core, and that class is missing at compile time, either add the missing dependency or remove the grouping bean temporarily. The simplest route for demo: keep the minimal OpenAPI bean (no GroupedOpenApi) or ensure springdoc-openapi-starter-webmvc-api is available.

Database & schema
The app uses JPA/Hibernate with spring.jpa.hibernate.ddl-auto=update. Example SQL to create DB:

sql
Copy code
CREATE DATABASE flowdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
If you want sample data to stop continuous executions:

sql
Copy code
-- disable scheduling for workflow ids 3 and 4
UPDATE workflows SET scheduled = 0 WHERE id IN (3,4);
Design decisions (short)
Persistence: JPA Entities for Workflow / Node / WorkflowRun / WorkflowRunLog.

Execution: WorkflowExecutionService performs node execution (HTTP/DELAY/CONDITION) inside a transaction; NotifyExecutor persists its own logs with REQUIRES_NEW to avoid locking the main flow.

Security: JWT tokens; endpoints are secured with role checks. Execution and scheduling are restricted to ADMIN.

Masking: Node configs are masked by default to hide secrets; admin can reveal via ?reveal=true.

Resilience: Execution logs each node run and survives partial failures.

Known issues & notes
Scheduler demo: some workflows may be set scheduled=true and run every ~60s (demo mode). Use the schedule disable endpoint to stop them.

Slack webhooks in sample data are masked — avoid sending to real webhooks in test mode.

If springdoc crashes at runtime, align dependency versions or temporarily remove custom OpenApi grouping.
