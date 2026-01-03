# Enterprise-Order-Suite
Enterprise-grade B2B Order Management System built with Java, Spring Boot, PostgreSQL and JWT authentication. Designed with clean architecture, workflow automation, and advanced business logic.

📦 B2B Order Management System

A production-grade backend system for managing purchase orders between companies (B2B).
Built with Java 17, Spring Boot 3, PostgreSQL, JWT Authentication, and Docker Compose.

This project showcases real-world backend development skills, including layered architecture, business rules, status workflows, auditing, filtering, pagination, and clean API design.

🚀 Features
🔐 Authentication

- User registration
- Login with JWT
- Refresh token
- Role-based authorization
- Protected API routes

🏢 Company Management

- Create, update, retrieve, deactivate companies
- Validation and business rules
- Pagination and search

📦 Product Management

- Create and manage products
- Update prices
- Archive/soft delete
- Filtering

🧾 Order Management

- Create new orders
- Associate buyer/seller companies
- Add multiple items
- Automatic total calculation
- Change order status (workflow)
- Full order retrieval with details

📜 Order Status Workflow

Supported statuses:

- CREATED
- APPROVED
- REJECTED
- PROCESSING
- SHIPPED
- COMPLETED

Includes:

- Validation of allowed transitions
- History entries created automatically

🕒 Order History

- Every status change logs:
- Previous status
- New status
- User responsible
- Timestamp

🧩 Advanced Filtering

- Filter orders by:
- Company
- Status
- Date range
- Value range

Pagination + sorting

🧭 Documentation

- Swagger/OpenAPI UI
- Clear folder structure
- Postman collection (optional)

🧱 Architecture Overview

The system follows a clean layered architecture:

Client (Postman / Frontend)
        ↓
Spring Boot (Controllers)
        ↓
Service Layer (Business logic)
        ↓
Repository Layer (JPA)
        ↓
PostgreSQL Database


Additional engineering practices:

- DTO mapping with MapStruct
- Global exception handling
- Validation with Jakarta Bean Validation
- Structured logging (SLF4J)
- JWT authentication with refresh tokens

🛠 Tech Stack
- Backend
- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security + JWT
- MapStruct
- Lombok
- Validation API
- Database
- PostgreSQL
- Flyway (optional for migrations)
- DevOps
- Docker
- Docker Compose
- Swagger/OpenAPI
- JUnit 5

🗃️ Database Entities

User

id, name, email, password, role

Company

id, name, documentNumber, active

Product

id, name, price, description, active

Order

id, buyerCompany, sellerCompany,
createdAt, updatedAt, status, totalValue

OrderItem

id, orderId, productId, quantity, priceAtMoment

OrderHistory

id, orderId, previousStatus, newStatus, changedBy, changedAt



📡 API Endpoints Overview

Auth

- POST /auth/register
- POST /auth/login
- POST /auth/refresh

Companies

- POST /companies
- GET /companies
- GET /companies/{id}
- PUT /companies/{id}
- DELETE /companies/{id}

Products

- POST /products
- GET /products
- GET /products/{id}
- PUT /products/{id}
- DELETE /products/{id}

Orders

- POST /orders
- GET /orders
- GET /orders/{id}
- PUT /orders/{id}/status
- GET /orders/{id}/history

🐳 How to Run with Docker

Make sure you have Docker installed, then run:

docker compose up -d

This will start:

- PostgreSQL
- The backend application

Database available at:

localhost:5432
user: ${DB_USER}
password: ${DB_PASSWORD}


▶️ How to Run Locally (Without Docker)
1. Start PostgreSQL manually
Create a database:
b2b_order_db

2. Set environment variables (or edit application.yml):
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/b2b_order_db
   username: ${DB_USER}
   password: ${DB_PASSWORD}

3. Run the app:
./mvnw spring-boot:run

(or use your IDE)

📁 Project Structure (Recommended)
src/main/java/com/yourname/b2border
    config/
    controllers/
    services/
    repositories/
    dtos/
    entities/
    exceptions/
    security/
    mappers/

🧪 Tests
Includes:

- Unit tests for services
- Integration tests for controllers
- Authentication flow tests

To run:
./mvnw test
