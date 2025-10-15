🌊 Flow Workflow Automation Backend

A Spring Boot–based backend system that allows users to design, execute, and automate workflows with customizable nodes — such as API calls, delays, conditions, and Slack notifications.

This project demonstrates a low-code workflow orchestration system, built for flexibility, scalability, and modularity.

🚀 Features

🔐 JWT Authentication with role-based access (ADMIN, USER)

⚙️ Workflow Creation, Execution & Scheduling

🧩 Multiple Node Types – API call, Delay, Conditional, Notify (Slack)

🕒 Automatic Scheduled Runs using CRON expressions

📜 Workflow Run Logs with detailed execution tracking

🌐 RESTful API Design documented using Swagger

🧱 Modular Layered Architecture (Controller → Service → Repository)

🧰 MySQL Integration for persistence and reliability

🧩 Architecture Overview
+-------------------------------------------------------------+
|                         Flow Backend                        |
|-------------------------------------------------------------|
|  Controllers  →  Services  →  Repositories  →  Database     |
|-------------------------------------------------------------|
|  AuthController        →  JWT Login / Register              |
|  WorkflowController    →  Workflow CRUD + Execution         |
|  WebhookController     →  External Triggers                 |
|  ScheduleService       →  Workflow Scheduling (CRON)        |
+-------------------------------------------------------------+

🛠️ Tech Stack
Layer	Technology
Backend Framework	Spring Boot 3.5.5
Security	Spring Security + JWT
Database	MySQL
API Docs	Springdoc OpenAPI (Swagger UI)
Logging	SLF4J, Spring Boot Logging
Tools	Postman, Maven, Git, GitHub
⚙️ Setup Instructions
1️⃣ Clone the Repository
git clone https://github.com/prajwagmode/workflow-backend.git
cd workflow-backend

2️⃣ Configure Database

Update the application.properties file:

spring.datasource.url=jdbc:mysql://localhost:3306/flowdb
spring.datasource.username=root
spring.datasource.password=root123

3️⃣ Build and Run
mvn clean package -DskipTests
java -jar target/workflow-0.0.1-SNAPSHOT.jar


Server will start on:
👉 http://localhost:8080

🔑 Authentication

Use the /auth/register and /auth/login endpoints.

Example Login:

POST /auth/login
{
  "username": "admin",
  "password": "admin123"
}


You’ll receive a JWT token.
Include it in headers for secured endpoints:

Authorization: Bearer <token>

🧠 Example Workflows
Workflow	Description
Daily Weather Report (ID: 1)	Fetches real-time weather data and logs results daily.
Weather API Flow (ID: 3)	Executes a multi-node flow that fetches weather info and posts summary to Slack.
Condition + Delay Demo (ID: 4)	Demonstrates conditional branching with a time delay between nodes.
Slack Notify Demo (ID: 5)	Sends notifications directly to Slack for alerting.
🌐 API Documentation (Swagger)

Once the app is running:
👉 Swagger UI: http://localhost:8080/swagger-ui.html

👉 OpenAPI JSON: http://localhost:8080/v3/api-docs

🧩 Folder Structure
src/main/java/com/flow/workflow/
│
├── controller/        # REST Controllers
├── service/           # Business Logic
├── repository/        # JPA Repositories
├── model/             # Entities & DTOs
├── config/            # Security & OpenAPI Config
└── security/          # JWT Token Utilities

🧠 Design Highlights

Layered Architecture — clear separation of concerns

Scalable Node Model — new node types can be added easily

Async Execution — background thread handling for workflow runs

Security-first — JWT and role-based access control

Auditable — run logs and execution history maintained

📦 Deliverables

✅ Source code hosted on GitHub

✅ Configurable backend API

✅ Swagger-based documentation

✅ Example workflows preconfigured in MySQL

✅ README documentation for setup and usage

✨ Future Enhancements

🧠 AI-assisted workflow suggestions

🕹️ Frontend Builder (React-based) for drag-and-drop workflow design

📩 Email & webhook integrations

🔄 Real-time execution monitoring dashboard

👨‍💻 Author

Prajwal Ramesh Wagmode
Developed as part of Flow Workflow Automation System
📍 Bengaluru, India
