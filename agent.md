## 🏢 1. Project Overview & Architecture
- **Architecture:** Modular Monolith using Java 17+ and Spring Boot 3.
- **Base Package:** `com.enterprise.ordersuite`
- **Build System:** Gradle
- **Database:** PostgresSQL with Flyway Migrations (`src/main/resources/db/migration`).
- **Goal:** Build the `orders` module to handle order lifecycle, ingestion, and async processing.

---

## 🛑 2. Reuse Existing Infrastructure
- Use the existing `com.enterprise.ordersuite.identity.application.CurrentUserService` to get the logged-in user.
- Use the existing `com.enterprise.ordersuite.common.util.PagedResult` for pagination.
- Use the existing `com.enterprise.ordersuite.api.errors.ApiErrorResponse` pattern for error handling.

---

## 📌 3. Current State Tracker
- [x] Security & JWT (`security`)
- [x] Auth Module (`auth`)
- [x] Identity & Roles (`identity`)
- [x] Phase 1: Order Domain & Core CRUD (`orders`)
- [x] Phase 2: Order Items & Product Association (`orders`)
- [x] Phase 3: Order State Machine, Audit & Async Processing
- [x] Phase 4: Product Module (Core Domain & CRUD)
    - [x] **Domain:** Created `Product` entity (name, SKU, description, price, stockQuantity).
    - [x] **Persistence:** Flyway migration `V13__create_products_table.sql` and `ProductRepository`.
    - [x] **API:** Implemented RESTful CRUD for products in `com.enterprise.ordersuite.products`.
    - [x] **Security:** Ensured only `ADMIN` role can perform write operations via `@PreAuthorize`.
    - [x] **Testing:** Added `ProductServiceTest` (Unit) and `ProductControllerIT` (Integration).
- [x] Phase 5: Inventory Management & Domain Integration
    - [x] **Service Refactor:** Refactored `ProductService` interface in `orders` and made `com.enterprise.ordersuite.products.application.service.ProductService` implement it to maintain module isolation while providing real functionality.
    - [x] **Inventory Logic:** Implemented `decrementStock` in `OrderService.createOrder`.
    - [x] **Restock Logic:** Implemented `incrementStock` when an order status transitions to `CANCELLED`.
    - [x] **Validation:** Added `InsufficientStockException` and logic to reject orders if stock is low.
    - [x] **Testing:** Updated `OrderServiceTest` and `OrderControllerIT` to verify inventory flows.
- [x] Phase 6: Advanced Authorization & Customer Profiles
    - [x] **Domain:** Formally linked `Order` to the `User` entity from the `identity` module via `V14__link_orders_to_users.sql`.
    - [x] **Ownership Security:** Implemented `@PreAuthorize` checks in `OrderService` using a custom `isOrderOwner` method to ensure users only access their own resources.
    - [x] **Admin Access:** Maintained global access for `ROLE_ADMIN` in all order operations.
    - [x] **Multi-tenancy filter:** Updated `searchOrders` and `getAllOrders` to automatically filter by `customerId` for non-admin users.
    - [x] **Module Isolation:** Enforced Dependency Inversion Principle by having the `products` module implement the `ProductService` interface defined in the `orders` module, ensuring strict isolation.
- [ ] Phase 7 (Optional): CSV Batch Ingestion
    - [ ] Implement multi-part file upload for bulk order/product ingestion.
    - [ ] Use `CompletableFuture` or Spring Batch for high-performance processing.
