# Orders Authorization Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the `isAdmin()` role-hierarchy defect that silently demotes `SUPER_ADMIN`, and collapse the three competing authorization styles in the `orders` module into one — without changing any permission the frontend can observe.

**Architecture:** Lock the current behaviour behind characterization tests first, then refactor underneath them. Authorization ends up expressed only in `@PreAuthorize`: the controller declares the coarse permission, the service declares resource ownership, and no permission decision survives inside a method body.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security method security, JUnit 5, Mockito, AssertJ, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md` (Part 2)

## Global Constraints

- **The permission matrix is frozen.** The only observable behaviour change permitted is the `SUPER_ADMIN` defect fix. (Spec D7)
- Authorization is expressed in `@PreAuthorize` only. Never inside a method body. (Spec D2)
- 4-space indentation in `src/main`, 2-space in `src/test`. (Spec D3)
- Full `./gradlew test` before any completion claim. Docker must be running. (Spec D5)
- Invoke the `spring-security-changes` skill before editing any file under `security/` or `auth/`. The `PreToolUse` hook will remind you.
- All code and comments in English. (Spec D6)

### The frozen matrix

| Endpoint | Effective today | Required after |
|---|---|---|
| `POST /orders` | any authenticated user | unchanged |
| `GET /orders/{id}` | owner or ADMIN+ | unchanged |
| `PUT /orders/{id}` | owner or ADMIN+ | unchanged |
| `DELETE /orders/{id}` | **ADMIN+ only** (the scope denies USER) | unchanged — but must become explicit |
| `GET /orders`, `/orders/search` | ADMIN sees all, USER sees own | unchanged, **except** SUPER_ADMIN now correctly sees all |

`DELETE` is the trap. `OrderService.deleteOrder:167` currently reads
`hasRole('ADMIN') or @orderService.isOrderOwner(...)`, and only the controller's
`SCOPE_order:delete` keeps a USER out. Deleting the scope without tightening the service
**widens** the permission. Task 3 handles this.

---

### Task 1: Characterization tests — freeze the matrix

Written first, and they must **pass against unmodified code**. They are the safety net the
refactor runs under. The one test that fails is the `SUPER_ADMIN` case, which is the defect.

**Files:**
- Modify: `src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java`

**Interfaces:**
- Consumes: the existing private helpers in that file — `createTestUser(roleName, firstName, lastName, email)` at line 341, `loginAndGetAccessToken(email, password)` at line 361, and `createOrderAsUser(token, userId, orderNumber)` which returns the new order's `Long` id.
- Produces: the frozen matrix, relied on by every later task.

- [ ] **Step 1: Read the existing helpers before writing anything**

Open `OrderControllerIT.java` and read lines 330–400. Match the existing style exactly:
2-space indentation, unique emails via `UUID`, `.as(...)` on non-obvious assertions.
Confirm the exact signature and return type of `createOrderAsUser` — the code below assumes
`Long createOrderAsUser(String token, Long customerId, String orderNumber)`. If it differs,
adapt the calls rather than the helper.

- [ ] **Step 2: Add a SUPER_ADMIN fixture to `setUp()`**

`SUPER_ADMIN` is seeded by `V15__seed_super_admin.sql`, so `roleRepository.findByName` resolves it.

Add the fields beside the existing `adminUser`/`regularUser`/`otherUser`:

```java
  private User superAdminUser;
  private String superAdminToken;
```

And at the end of `setUp()`:

```java
    superAdminUser = createTestUser(
      "SUPER_ADMIN",
      "Super",
      "Admin",
      "superadmin-" + UUID.randomUUID() + "@test.com"
    );

    superAdminToken = loginAndGetAccessToken(
      superAdminUser.getEmail(),
      DEFAULT_PASSWORD
    );
```

- [ ] **Step 3: Write the characterization tests**

Append these to the class. Five of the six pass today; `getAllOrders_asSuperAdmin_seesOrdersFromEveryCustomer` is the defect and must fail.

```java
  @Test
  void createOrder_asRegularUser_returns201() throws Exception {
    OrderCreateRequest request = orderCreateRequestFor(
      "ORD-FROZEN-CREATE-" + UUID.randomUUID()
    );

    mockMvc.perform(post("/orders")
        .header("Authorization", "Bearer " + userToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isCreated());
  }

  @Test
  void updateOrder_asNonOwner_returns403() throws Exception {
    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-FROZEN-UPD-" + UUID.randomUUID()
    );

    OrderUpdateRequest request = new OrderUpdateRequest();
    request.setStatus(OrderStatus.PROCESSING);

    mockMvc.perform(put("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + otherToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isForbidden());
  }

  @Test
  void deleteOrder_asOwner_returns403() throws Exception {
    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-FROZEN-DEL-OWNER-" + UUID.randomUUID()
    );

    mockMvc.perform(delete("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + userToken))
      .andExpect(status().isForbidden());
  }

  @Test
  void deleteOrder_asAdmin_returns204() throws Exception {
    Long orderId = createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-FROZEN-DEL-ADMIN-" + UUID.randomUUID()
    );

    mockMvc.perform(delete("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isNoContent());
  }

  @Test
  void getAllOrders_asRegularUser_seesOnlyOwnOrders() throws Exception {
    createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-FROZEN-MINE-" + UUID.randomUUID()
    );
    createOrderAsUser(
      otherToken,
      otherUser.getId(),
      "ORD-FROZEN-THEIRS-" + UUID.randomUUID()
    );

    mockMvc.perform(get("/orders")
        .header("Authorization", "Bearer " + userToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[?(@.customerId != " + regularUser.getId() + ")]")
        .isEmpty());
  }

  @Test
  void getAllOrders_asSuperAdmin_seesOrdersFromEveryCustomer() throws Exception {
    createOrderAsUser(
      userToken,
      regularUser.getId(),
      "ORD-FROZEN-SA-A-" + UUID.randomUUID()
    );
    createOrderAsUser(
      otherToken,
      otherUser.getId(),
      "ORD-FROZEN-SA-B-" + UUID.randomUUID()
    );

    mockMvc.perform(get("/orders")
        .header("Authorization", "Bearer " + superAdminToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.content[?(@.customerId == " + regularUser.getId() + ")]")
        .isNotEmpty())
      .andExpect(jsonPath("$.content[?(@.customerId == " + otherUser.getId() + ")]")
        .isNotEmpty());
  }
```

Add a private helper if `orderCreateRequestFor` does not already exist, mirroring how
`createOrderAsUser` builds its request. Add the missing static imports for `delete`.

- [ ] **Step 4: Run them and record which fail**

Run:
```bash
./gradlew test --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT"
```

Expected: all pass **except** `getAllOrders_asSuperAdmin_seesOrdersFromEveryCustomer`,
which fails because the SUPER_ADMIN is demoted and sees only their own orders — which is
none, since the two orders belong to other users.

Paste the actual failure output into your report. If any *other* test fails, stop: the
matrix is not what the spec recorded, and the discrepancy must be resolved before
refactoring.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java
git commit -m "test: freeze the /orders permission matrix and expose the SUPER_ADMIN defect"
```

---

### Task 2: Fix `isAdmin()` to respect the role hierarchy

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/orders/application/service/OrderService.java:209-212`
- Modify: `src/test/java/com/enterprise/ordersuite/orders/application/service/OrderServiceTest.java`

**Interfaces:**
- Consumes: the `RoleHierarchy` bean defined at `security/config/SecurityConfig.java:53`.
- Produces: an `OrderService` constructor that now takes `RoleHierarchy` as its eighth
  dependency. `OrderServiceTest` must mock it.

- [ ] **Step 1: Understand the defect before editing**

`RoleHierarchyImpl.fromHierarchy("ROLE_SUPER_ADMIN > ROLE_ADMIN \n ROLE_ADMIN > ROLE_USER")`
is wired into `methodSecurityExpressionHandler` only. It expands authorities **inside**
`@PreAuthorize`. It does not touch `authentication.getAuthorities()`, which returns exactly
what `JwtAuthenticationFilter` put there — for a SUPER_ADMIN, that is `ROLE_SUPER_ADMIN`
and nothing else. So `equals("ROLE_ADMIN")` is false and the user is treated as a regular
customer by `getAllOrders` and `searchOrders`.

- [ ] **Step 2: Add the dependency**

Add the import:

```java
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
```

Add the field to the `@RequiredArgsConstructor` block, after `notificationService`:

```java
    private final RoleHierarchy roleHierarchy;
```

- [ ] **Step 3: Replace `isAdmin()`**

Replace lines 209-212 with:

```java
    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return false;
        }

        return roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities())
                .stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
```

Add the import:

```java
import org.springframework.security.core.Authentication;
```

The null guard is not defensive padding — `getAllOrders` is reachable from a scheduled
context in principle, and `getAuthorities()` on a null authentication is an NPE rather than
a 403.

- [ ] **Step 4: Keep the unit test compiling**

`@InjectMocks` will now inject a mock `RoleHierarchy` whose
`getReachableGrantedAuthorities` returns `null`, producing an NPE in any test that reaches
`isAdmin()`. Add the mock and an identity stub so unit tests keep their existing meaning;
the hierarchy itself is proven by the integration test, against the real bean.

In `OrderServiceTest`, add the import and field:

```java
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
```

```java
  @Mock
  private RoleHierarchy roleHierarchy;
```

And in `setUp()`, beside the existing `lenient()` stubs:

```java
    lenient()
      .when(roleHierarchy.getReachableGrantedAuthorities(any()))
      .thenAnswer(invocation -> invocation.getArgument(0));
```

- [ ] **Step 5: Run the tests**

Run:
```bash
./gradlew test --tests "com.enterprise.ordersuite.orders.application.service.OrderServiceTest" --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT"
```

Expected: **all** pass, including
`getAllOrders_asSuperAdmin_seesOrdersFromEveryCustomer`, which failed in Task 1.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/orders/application/service/OrderService.java \
        src/test/java/com/enterprise/ordersuite/orders/application/service/OrderServiceTest.java
git commit -m "fix(orders): resolve isAdmin through the role hierarchy so SUPER_ADMIN is not demoted"
```

---

### Task 3: Make `DELETE` admin-only explicitly, then drop the fabricated scopes

Order matters. Tighten the service **before** removing the scope that is currently doing the
work, or there is a commit in history where any user can delete any order they own.

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/orders/application/service/OrderService.java:167`
- Modify: `src/main/java/com/enterprise/ordersuite/security/jwt/JwtAuthenticationFilter.java:63-75`
- Modify: `src/main/java/com/enterprise/ordersuite/orders/api/OrderController.java` (the six `@PreAuthorize` lines)

**Interfaces:**
- Consumes: the characterization tests from Task 1, which must stay green throughout.

- [ ] **Step 1: Invoke the security skill**

This task edits `security/jwt/`. Invoke `spring-security-changes` and follow it. The hook
will remind you; do not dismiss it.

- [ ] **Step 2: Tighten `deleteOrder`**

At `OrderService.java:167`, replace:

```java
    @PreAuthorize("hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)")
    public void deleteOrder(Long id) {
```

with:

```java
    // Deleting an order is ADMIN-only. This was previously enforced by the
    // controller withholding SCOPE_order:delete from regular users; stating it
    // here keeps the permission identical once that scope is removed.
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteOrder(Long id) {
```

- [ ] **Step 3: Verify the matrix is still frozen**

Run:
```bash
./gradlew test --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT"
```
Expected: all pass. In particular `deleteOrder_asOwner_returns403` and
`deleteOrder_asAdmin_returns204` still hold — the enforcement moved, the behaviour did not.

- [ ] **Step 4: Remove the fabricated scopes**

In `JwtAuthenticationFilter.java`, delete the whole block at lines 63-75 — the comment
`// Map roles to scopes for PreAuthorize compatibility` through the closing brace of the
`else if` — leaving the `ROLE_` mapping above it untouched.

These are not OAuth2 scopes. They are not claims, they are derived from the role that is
already present, `SCOPE_order:write` is granted to every authenticated user so it denies
nobody, and a SUPER_ADMIN receives none of them because neither branch matches
`roles.contains("ADMIN")` or `roles.contains("USER")`.

- [ ] **Step 5: Replace the controller's scope expressions**

In `OrderController.java`, the six `@PreAuthorize` lines now reference authorities that no
longer exist. Replace them so the coarse permission is stated directly:

| Line | Was | Becomes |
|---|---|---|
| 35 `createOrder` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:write')` | `isAuthenticated()` |
| 45 `getOrderById` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:read')` | `isAuthenticated()` |
| 60 `getAllOrders` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:read')` | `isAuthenticated()` |
| 69 `searchOrders` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:read')` | `isAuthenticated()` |
| 84 `updateOrder` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:write')` | `isAuthenticated()` |
| 104 `deleteOrder` | `hasRole('ADMIN') or hasAuthority('SCOPE_order:delete')` | `hasRole('ADMIN')` |

Every one of these was already satisfied by any authenticated user except `delete`, so this
is a faithful restatement. Ownership stays where it belongs — on the service.

- [ ] **Step 6: Confirm no other caller depended on the scopes**

Run:
```bash
grep -rn "SCOPE_" src/main src/test --include=*.java
```
Expected: **no output**. If anything matches, it was relying on a fabricated authority and
must be handled before continuing.

- [ ] **Step 7: Run the full suite**

Run:
```bash
./gradlew test
```
Expected: green. The scopes were load-bearing only for `DELETE`, which Task 3 Step 2
already covered.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/orders/application/service/OrderService.java \
        src/main/java/com/enterprise/ordersuite/security/jwt/JwtAuthenticationFilter.java \
        src/main/java/com/enterprise/ordersuite/orders/api/OrderController.java
git commit -m "refactor(security): drop fabricated SCOPE_order authorities, state DELETE as ADMIN-only"
```

---

### Task 4: Remove the controller's duplicate authorization

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/orders/api/OrderController.java` — remove
  `@PostAuthorize` at line 46, the `if` block at 92-94, the `if` block at 112-114, and the
  `isAdmin()` helper at 124-127.

**Interfaces:**
- Consumes: the characterization tests from Task 1.
- Produces: an `OrderController` with no authorization logic in any method body, and no
  remaining use of `CurrentUserService` if nothing else needs it.

- [ ] **Step 1: Remove the `@PostAuthorize`**

Delete line 46 entirely:

```java
    @PostAuthorize("hasRole('ADMIN') or returnObject.body.customerId == authentication.principal.id")
```

It is redundant — `OrderService.getOrderById:79` already denies a non-owner before the
order is ever loaded. Filtering afterwards means the work already happened.

- [ ] **Step 2: Remove the manual check in `updateOrder`**

Replace the body at lines 90-100 so the mapping no longer branches on identity:

```java
        return orderService.updateOrder(id, request)
                .map(orderResponse -> new ResponseEntity<>(orderResponse, HttpStatus.OK))
                .orElseGet(() -> {
                    log.warn("requestId: {} - Order with ID: {} not found for update.", requestId, id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
```

`OrderService.updateOrder:118` already carries
`hasRole('ADMIN') or @orderService.isOrderOwner(#id, principal.id)`, so a non-owner never
reaches this line.

- [ ] **Step 3: Remove the manual check in `deleteOrder`**

Replace the body at lines 110-121:

```java
        return orderService.getOrderById(id)
                .map(order -> {
                    orderService.deleteOrder(id);
                    return new ResponseEntity<Void>(HttpStatus.NO_CONTENT);
                })
                .orElseGet(() -> {
                    log.warn("requestId: {} - Order with ID: {} not found for deletion.", requestId, id);
                    return new ResponseEntity<>(HttpStatus.NOT_FOUND);
                });
```

- [ ] **Step 4: Delete the `isAdmin()` helper and its now-unused imports**

Remove lines 124-127 in full. Then remove `import org.springframework.security.access.prepost.PostAuthorize;`
and the `CurrentUserService` field and import **only if** nothing else in the class still
uses them. Compile to find out rather than guessing.

- [ ] **Step 5: Run the full suite**

Run:
```bash
./gradlew test
```
Expected: green, with the Task 1 matrix tests unchanged. They are the proof that removing
three layers of duplicate checking changed no observable behaviour.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/orders/api/OrderController.java
git commit -m "refactor(orders): remove duplicate authorization from the controller"
```

---

### Task 5: Independent verification

**Files:**
- None. This task produces evidence.

- [ ] **Step 1: Full suite from clean**

Run:
```bash
./gradlew clean test
```
Expected: green. Paste the real summary line into your report.

- [ ] **Step 2: Confirm no authorization logic survives in a method body**

Run:
```bash
grep -rn "getAuthorities()" src/main --include=*.java
grep -rn "isAdmin()" src/main --include=*.java
```
Expected: `OrderController` appears in neither. `OrderService.isAdmin()` remains, and is
the only survivor — it now resolves through `RoleHierarchy` and exists because
`getAllOrders`/`searchOrders` scope a **query**, which an annotation cannot express.

- [ ] **Step 3: Dispatch the security reviewer**

Dispatch `spring-security-reviewer`:

> Audit the authorization of the `orders` module after the authorization refactor —
> `OrderController`, `OrderService` and `JwtAuthenticationFilter`. The intent was to fix the
> `SUPER_ADMIN` hierarchy defect and collapse three authorization styles into one, with no
> other permission change. Confirm whether any permission was widened or narrowed.

Expected: it reports the `isAdmin()` defect and the fabricated scopes as **resolved**, and
finds no widened permission. Treat any claim of a widened permission as blocking until you
have disproved it against the Task 1 matrix.

- [ ] **Step 4: Report the diff for review**

Run:
```bash
git log --oneline main..HEAD
git diff main...HEAD --stat
```

Summarise for the user: what changed, what stayed identical, and the one intentional
behaviour change — SUPER_ADMIN now sees all orders on `GET /orders` and `/orders/search`.

---

## Notes for the executor

- **The matrix is the contract.** If a refactor step turns a Task 1 test red, the refactor
  is wrong — do not adjust the test to match the new behaviour. The only test that is
  *expected* to change state is the SUPER_ADMIN one, and only in Task 2.
- **Task 3 Step 2 before Step 4, always.** Reversing them creates a commit where a regular
  user can delete their own orders.
- **`isOrderOwner` is scheduled for deletion.** In the restaurant-ops target model, orders
  belong to anonymous customers rather than `User` accounts, so ownership stops being a
  concept. Do not invest in generalising it — this plan makes it consistent, not permanent.
