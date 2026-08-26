# Enterprise Order Suite

### Production-oriented B2B Order Management Backend built with Java 17 and Spring Boot 3

**Enterprise Order Suite (EOS)** is a backend system for managing B2B purchasing workflows between companies. It demonstrates production-oriented Java backend engineering across **REST API design, Spring Boot, Spring Security, JWT authentication, hierarchical RBAC, PostgreSQL, JPA, Flyway, object storage, Docker, and automated integration testing with Testcontainers**.

The project focuses on the engineering problems that matter in real backend systems: **security, business rules, data integrity, API design, maintainability, request traceability, resilience, and automated testing**.

> **Target role:** Java Backend Developer | Java + Spring Boot | REST APIs | PostgreSQL | Spring Security | Docker | Automated Testing

---

## 🎯 Why This Project Exists

EOS was built as a realistic backend rather than a simple CRUD application.

The system models a B2B environment where users operate within an authenticated system, companies exchange purchase orders, products have historical pricing, and orders move through controlled business workflows.

The project intentionally incorporates engineering concerns commonly found in professional Java backend development:

- Secure authentication and authorization
- Business-rule enforcement
- Transactional persistence
- Relational data modeling
- Version-controlled database migrations
- API validation and error handling
- Request tracing
- Abuse protection
- Object storage
- Integration testing against real infrastructure
- Containerized development

---

# ⚡ Technical Snapshot

| Area | Implementation |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3 |
| API | REST |
| Security | Spring Security + JWT |
| Authorization | Hierarchical RBAC |
| Persistence | Spring Data JPA / Hibernate |
| Database | PostgreSQL |
| Migrations | Flyway |
| Object Storage | MinIO / S3-compatible |
| Mapping | MapStruct |
| Validation | Jakarta Bean Validation |
| Email | Spring Mail + Thymeleaf |
| Testing | JUnit 5 + Mockito + MockMvc |
| Integration Testing | Testcontainers |
| Containers | Docker / Docker Compose |
| Build | Gradle |
| API Documentation | OpenAPI / Swagger |
| Logging | SLF4J + MDC |

---

# 🏗️ Architecture

EOS follows a **modular, feature-oriented architecture** with clear separation between API, application, domain, and persistence concerns.

Business capabilities are organized by feature rather than placing every controller, service, and repository into a single global technical layer.

```text
src/main/java/com/enterprise/ordersuite
│
├── auth/
├── identity/
├── company/
├── products/
├── orders/
├── profile/
├── notifications/
├── security/
└── support/
````

Within individual features, responsibilities are further separated according to their role in the application:

```text
Feature
│
├── api/
│   ├── controllers
│   └── DTOs
│
├── application/
│   └── business/application services
│
├── domain/
│   └── domain models and rules
│
└── persistence/
    └── repositories and persistence concerns
```

A simplified request flow looks like:

```text
HTTP Request
     │
     ▼
Spring Security
     │
     ├── JWT Authentication
     ├── Authorization
     └── Security Filters
     │
     ▼
REST Controller
     │
     ▼
Application Layer
     │
     ├── Validation
     ├── Business Rules
     └── Transactions
     │
     ▼
Persistence Layer
     │
     ▼
PostgreSQL
```

This structure keeps business capabilities cohesive while maintaining clear boundaries between HTTP/API concerns, application logic, domain behavior, and persistence.

The architecture also keeps infrastructure-specific concerns isolated where practical, allowing implementations such as object storage to be changed without unnecessarily coupling business logic to a specific provider.

---

# 🔐 Authentication & Authorization

Security is one of the core areas of EOS.

## Authentication

The application implements:

* User registration
* JWT access tokens
* Refresh tokens
* Secure logout
* Password hashing
* Password reset workflow
* Password reset token persistence
* Password history enforcement
* Account activation/deactivation

## Authorization

EOS uses Spring Security's role hierarchy:

```text
ROLE_SUPER_ADMIN
        │
        ▼
   ROLE_ADMIN
        │
        ▼
   ROLE_USER
```

New users are explicitly restricted to the standard `USER` role during public registration, preventing clients from assigning themselves privileged roles.

Authorization is enforced at the API/security layer rather than relying solely on frontend behavior.

---

# 🛡️ Security Engineering

EOS goes beyond basic JWT authentication and implements additional defensive controls.

## Dual-Key Rate Limiting

Authentication-related requests can be constrained using both:

```text
Client IP
+
Target account email
```

This provides protection against credential-stuffing and brute-force scenarios where attackers attempt to distribute requests across multiple addresses.

## Request Payload Protection

Incoming request payload sizes can be rejected early using request metadata before unnecessarily processing oversized JSON bodies.

This provides an additional defense against memory-exhaustion attacks.

## Request Correlation

Each request receives a unique `requestId`.

The identifier is propagated through:

* MDC logging context
* Application logs
* HTTP response headers

This allows individual requests to be traced through server logs during debugging and incident investigation.

---

# 👤 User Profiles & Object Storage

EOS includes user profile management with avatar support.

The profile functionality includes:

* Retrieve authenticated user profile
* Update profile information
* Upload avatar
* Replace avatar
* Delete avatar
* Protected profile endpoints

Avatars and application media are stored using **MinIO**, an S3-compatible object storage platform suitable for local development.

The storage implementation is isolated from business logic, allowing the underlying storage provider to be replaced later without coupling domain functionality to a specific storage vendor.

```text
Profile Feature
      │
      ▼
Storage Service
      │
      ▼
S3-Compatible API
      │
      ▼
MinIO
```

---

# 🏢 Company Management

Companies represent organizations participating in B2B transactions.

Supported operations include:

* Create company
* Retrieve company
* Update company
* Deactivate company
* Search
* Pagination
* Validation

---

# 📦 Product Management

Products represent goods available for B2B orders.

Supported functionality includes:

* Product creation
* Product retrieval
* Product updates
* Price updates
* Archiving/deactivation
* Filtering
* Validation

An important business rule is that an order item preserves the **price at the time the order was created**.

This prevents future product price changes from modifying historical order totals.

---

# 🧾 Order Management

Orders are the primary business domain of EOS.

An order can contain:

* Buyer company
* Seller company
* Multiple order items
* Product references
* Quantities
* Historical item prices
* Current status
* Calculated total value

The backend calculates order totals from persisted order-item information rather than trusting a client-provided total.

This keeps financial calculations under server-side business control.

---

# 🔄 Order Workflow

Orders follow a controlled lifecycle:

```text
CREATED
   │
   ├────► REJECTED
   │
   ▼
APPROVED
   │
   ▼
PROCESSING
   │
   ▼
SHIPPED
   │
   ▼
COMPLETED
```

The backend validates allowed status transitions before modifying an order.

Invalid transitions are rejected rather than allowing clients to arbitrarily manipulate the order lifecycle.

This keeps workflow rules inside the backend where they can be consistently enforced regardless of the client consuming the API.

---

# 📜 Order History & Auditability

Every order status transition creates a historical record containing:

* Previous status
* New status
* User responsible for the change
* Timestamp
* Associated order

This provides an auditable representation of the order lifecycle.

---

# 🔎 Advanced Filtering

Orders can be queried using multiple filtering criteria, including:

* Company
* Status
* Date range
* Value range

The API also supports:

* Pagination
* Sorting

Filtering and pagination are handled server-side so clients do not need to retrieve the entire dataset before applying filters.

---

# 🗄️ Persistence & Database Engineering

EOS uses **PostgreSQL** with **Spring Data JPA / Hibernate**.

The database schema is managed through **Flyway migrations**.

This provides:

* Version-controlled schema evolution
* Deterministic database initialization
* Reproducible environments
* Safe incremental schema changes
* Migration history

Core persistence concepts include:

```text
User
Role
UserProfile
Company
Product
Order
OrderItem
OrderHistory
PasswordResetToken
PasswordHistory
```

The relational model is intentionally designed around the business domain rather than treating the application as a collection of independent CRUD tables.

---

# 🧪 Automated Testing

Testing is an important part of EOS architecture.

The project uses:

* JUnit 5
* Mockito
* Spring Boot Test
* MockMvc
* Spring Security Test
* Testcontainers
* PostgreSQL

## Unit Tests

Unit tests are used where isolated business logic can be tested without requiring the complete Spring application context.

Typical candidates include:

* Business rules
* Validation logic
* Security-related logic
* Service behavior

## Integration Tests

Integration tests verify real application behavior using:

```text
Spring Boot
      +
Spring Security
      +
PostgreSQL
      +
Flyway
      +
HTTP / MockMvc
```

PostgreSQL is provided through **Testcontainers**, meaning integration tests run against an actual PostgreSQL database inside a Docker container rather than relying on a developer's local database.

The project centralizes the PostgreSQL test infrastructure and uses Spring Boot's `@ServiceConnection` mechanism to provide the container connection details to the application context.

This makes the tests reproducible across developer machines and CI environments.

Integration tests cover application behavior through the real Spring context, security configuration, persistence layer, database schema, and HTTP layer rather than replacing these components with mocks.

---

# 🐳 Containerized Development

EOS uses Docker Compose for local infrastructure.

```text
┌──────────────────────────────┐
│       Enterprise Order Suite │
│                              │
│      Spring Boot Backend     │
│              │               │
│       ┌──────┴──────┐        │
│       ▼             ▼        │
│  PostgreSQL       MinIO      │
│                              │
└──────────────────────────────┘
```

Start the infrastructure:

```bash
docker compose up -d
```

Stop it:

```bash
docker compose down
```

Docker is also required for Testcontainers integration tests.

---

# 📡 REST API

EOS exposes RESTful endpoints organized around business capabilities.

## Authentication

```http
POST /auth/register
POST /auth/login
POST /auth/refresh
POST /auth/forgot-password
POST /auth/reset-password
POST /auth/logout
```

## Profile

```http
GET    /api/me/profile
PUT    /api/me/profile
POST   /api/me/profile/avatar
DELETE /api/me/profile/avatar
```

## Companies

```http
POST   /companies
GET    /companies
GET    /companies/{id}
PUT    /companies/{id}
DELETE /companies/{id}
```

## Products

```http
POST   /products
GET    /products
GET    /products/{id}
PUT    /products/{id}
DELETE /products/{id}
```

## Orders

```http
POST /orders
GET /orders
GET /orders/{id}
PUT /orders/{id}/status
GET /orders/{id}/history
```

API contracts are documented using OpenAPI/Swagger.

---

# 📚 Engineering Decisions

One of the goals of EOS is to demonstrate **engineering judgment**, not just technology usage.

## Why PostgreSQL?

The core domain contains strongly related entities, transactional workflows, historical records, and relational constraints.

PostgreSQL provides a strong fit for this data model while supporting transactional consistency and complex querying.

## Why Flyway?

Database schema changes are treated as version-controlled application artifacts instead of relying on manually synchronized developer databases.

## Why Testcontainers?

Integration tests should validate behavior against the same type of database the application uses in real environments.

Testcontainers provides disposable infrastructure that can be created and destroyed automatically during testing.

## Why MinIO?

MinIO provides an S3-compatible local object-storage environment without requiring a cloud account during development.

This allows the application to develop against an object-storage API while keeping local development self-contained.

## Why Feature-Oriented Modular Architecture?

The application is organized around business capabilities while maintaining clear boundaries between API, application, domain, and persistence concerns.

This provides a balance between:

* Feature cohesion
* Separation of concerns
* Testability
* Maintainability
* Clear ownership of business logic
* Reduced coupling between unrelated features

Rather than forcing the entire application into a single global controller/service/repository structure, each business capability has its own cohesive module.

---

# 🔎 What This Project Demonstrates

EOS is intentionally designed to demonstrate the skills expected from a modern Java backend engineer.

## Java & Spring

* Java 17
* Spring Boot 3
* Spring Web
* Spring Data JPA
* Hibernate
* Spring Security
* Dependency Injection
* Transaction management
* REST API development

## API Engineering

* RESTful endpoint design
* DTO-based contracts
* Validation
* HTTP status handling
* Global exception handling
* OpenAPI documentation
* Pagination
* Filtering
* Sorting

## Security

* JWT
* Refresh tokens
* Password hashing
* Password history
* Secure password reset
* Role hierarchy
* Rate limiting
* Request-size protection
* Request tracing

## Database

* PostgreSQL
* SQL
* JPA/Hibernate
* Relational modeling
* Transactions
* Flyway migrations

## Testing

* JUnit 5
* Mockito
* MockMvc
* Spring Security Test
* Integration testing
* Testcontainers
* Real PostgreSQL integration

## Infrastructure

* Docker
* Docker Compose
* MinIO
* Environment-based configuration
* Reproducible test infrastructure

---

# ▶️ Running the Project

## Requirements

Install:

* Java 17+
* Docker Desktop
* Git

Clone the repository:

```bash
git clone <repository-url>
cd Enterprise-Order-Suite
```

Start infrastructure:

```bash
docker compose up -d
```

Start the application:

```bash
./gradlew bootRun
```

On Windows Git Bash:

```bash
./gradlew bootRun
```

---

# 🧪 Running Tests

Run the complete test suite:

```bash
./gradlew test
```

Run a specific test class:

```bash
./gradlew test --tests "com.enterprise.ordersuite.<TestClass>"
```

Run with additional Gradle diagnostics:

```bash
./gradlew test --info
```

Docker must be running because integration tests use Testcontainers.

---

# 📁 Project Structure

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── enterprise/
│   │           └── ordersuite/
│   │               ├── auth/
│   │               ├── identity/
│   │               ├── company/
│   │               ├── products/
│   │               ├── orders/
│   │               ├── profile/
│   │               ├── notifications/
│   │               ├── security/
│   │               └── support/
│   │
│   └── resources/
│       ├── application.yml
│       ├── db/
│       │   └── migration/
│       └── templates/
│
└── test/
    └── java/
```

---

# 💼 Why EOS Is Relevant to Java Backend Roles

EOS intentionally covers a broad set of backend engineering concerns found in modern Java development roles.

The project demonstrates hands-on experience across:

```text
Java 17
   │
Spring Boot 3
   │
REST APIs
   │
Spring Security + JWT
   │
Hierarchical RBAC
   │
JPA / Hibernate
   │
PostgreSQL
   │
Flyway
   │
JUnit + Mockito
   │
MockMvc
   │
Testcontainers
   │
Docker
   │
MinIO / S3-compatible storage
```

The project therefore serves as a practical demonstration of backend engineering rather than a collection of tutorial-level CRUD examples.

---

# 🚧 Potential Evolution

The architecture is intentionally designed so that additional infrastructure and distributed-system capabilities can be introduced without rewriting the existing business domains.

Potential future evolution includes:

* CI/CD pipelines
* Cloud deployment
* AWS S3 integration
* Redis caching
* Asynchronous messaging
* Kafka
* Microservice extraction where justified
* Centralized observability
* Metrics and distributed tracing
* Performance testing
* API integration testing against external services

These are **future architectural directions**, not technologies currently claimed as implemented.

---

# 👨‍💻 Engineering Focus

Enterprise Order Suite is built around one principle:

> **Build the backend as if another engineering team will have to maintain it.**

That means prioritizing:

* Explicit business rules
* Secure defaults
* Maintainable architecture
* Reproducible environments
* Automated tests
* Real database integration
* Clear API contracts
* Defensive programming
* Traceable application behavior
* Infrastructure that can evolve with the system

---

## License

This project is maintained as a personal software engineering project and portfolio demonstration.

```
```
