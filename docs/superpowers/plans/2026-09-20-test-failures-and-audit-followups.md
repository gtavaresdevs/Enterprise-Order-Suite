# Test Failures and Audit Follow-ups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Clear the five long-standing test failures on `feature/ai-agent` by fixing their
root causes, then action the four follow-ups raised by the `spring-security-reviewer` audit of
the orders authorization refactor.

**Context:** The orders authorization work is complete and merged into the branch
(`bbfc489..4b3535c`, see `2026-09-20-orders-authorization-fix.md`). That plan's status block
lists these follow-ups; this plan executes them. The five test failures predate all of it —
they fail identically at base commit `7f27a55`.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security method security, Apache Tika, AWS SDK
v2 / MinIO, JUnit 5, Mockito, AssertJ, Testcontainers.

## Global Constraints

- Invoke `spring-security-changes` before touching `security/`, `auth/` or any `@PreAuthorize`;
  `writing-backend-tests` before writing or changing any test; `flyway-migrations` before any
  migration or entity schema change. The `PreToolUse` hook will ask for confirmation.
- 4-space indentation in `src/main`, 2-space in `src/test`.
- All code and comments in English.
- **The baseline is 180/185, not green.** Those five failures are the subject of Phase A. Any
  failure outside the five named here is new and must be fixed, not explained away.
- Run the full `./gradlew test` (Docker required) before any completion claim.

## Decisions already taken by the user (2026-09-20)

1. **Narrow the orders endpoints** to `hasAnyRole('USER','ADMIN')` — Task 6.
2. **Fix stock and pricing now**, not at the restaurant-ops migration — Task 7.
3. **Root super admin becomes env-driven**; local/dev only, so `V15` may be edited in place —
   Task 8. The user rotates the exposed password themselves; the code change only stops the
   pattern recurring, since the old hash remains in git history regardless.

---

# Phase A — the five failing tests

Four root causes. Each task states whether the defect is in production code or in the test,
because three of the four are production bugs and must not be "fixed" by relaxing the test.

### Task 1: `AvatarValidator` never compares declared against detected content type

**Defect: production.** `AvatarValidator.validate` checks that the *declared* content type is
in `ALLOWED_CONTENT_TYPES`, then that the *Tika-detected* type is in the same set — but never
that the two agree. JPEG bytes declared as `image/png` satisfy both checks and pass.

**Fixes:** `AvatarValidatorTest.validate_JpegContentDeclaredAsPng_ThrowsInvalidAvatarException`

**Files:** `src/main/java/com/enterprise/ordersuite/profile/application/service/AvatarValidator.java`

- [ ] **Step 1:** In the `try` block, after `tika.detect(...)`, reject when the detected type
  is not in `ALLOWED_CONTENT_TYPES` **or** does not equal `declaredContentType`. Keep the
  existing message verbatim — both the mismatch test and
  `validate_MismatchedContentType_ThrowsInvalidAvatarException` assert
  `"Uploaded file is not a valid JPEG, PNG, or WebP image"`.
- [ ] **Step 2:** `./gradlew test --tests "*AvatarValidatorTest"` — expect all green.

### Task 2: `ProfileService` discards the key returned by `upload()`

**Defect: production and test.** `ProfileService.uploadAvatar:86-96` builds
`"avatars/" + UUID.randomUUID() + ".webp"`, passes it to `objectStorageService.upload(...)`,
discards the `String` the interface returns, and stores its own local variable. If any
implementation ever normalised or prefixed the key, the database would hold a key that does
not exist in the bucket.

The test is *also* wrong: it verifies `upload(eq("avatars/generated.webp"), ...)`, a literal
the production code can never produce, and there is no key-generator mock that could make it
do so. Asserting an exact value for a deliberately random key proves nothing. **Do not
introduce an injectable key generator** — no behaviour depends on the key's value, only on its
uniqueness, so that abstraction would exist solely to satisfy a test.

**Fixes:** `ProfileServiceTest.uploadAvatar_ValidFile_UploadsProcessedImageAndUpdatesProfile`,
`ProfileServiceTest.uploadAvatar_ReplacesExistingAvatar_DeletesOldAvatar`

**Files:**
- `src/main/java/com/enterprise/ordersuite/profile/application/service/ProfileService.java`
- `src/test/java/com/enterprise/ordersuite/profile/application/service/ProfileServiceTest.java`

- [ ] **Step 1:** Assign `upload(...)`'s return value and store *that* on the profile:
  `String storedAvatarKey = objectStorageService.upload(newAvatarKey, ...);`
  then `profile.setAvatarKey(storedAvatarKey);`. This alone fixes
  `uploadAvatar_ReplacesExistingAvatar_DeletesOldAvatar`, whose strict-stubbing mismatch is
  `getUrl` being called with the generated key instead of the stubbed one.
- [ ] **Step 2:** In `uploadAvatar_ValidFile_...`, replace the `verify(...).upload(eq(avatarKey), ...)`
  with an `ArgumentCaptor<String>` on the key and assert its **shape** —
  `startsWith("avatars/")`, `endsWith(".webp")` — with `.as(...)` explaining why the exact
  value is not asserted. Keep `assertThat(profile.getAvatarKey()).isEqualTo(avatarKey)`: it now
  passes and is the meaningful assertion, proving the profile stores what storage returned.
- [ ] **Step 3:** `./gradlew test --tests "*ProfileServiceTest"` — expect all green.

### Task 3: `InvalidAvatarException` is unmapped, so a bad upload is a 500

**Defect: production.** `InvalidAvatarException` extends `RuntimeException` and has **no**
`@ExceptionHandler` anywhere. It falls through to a catch-all `RuntimeException` handler and
returns 500 where the contract calls for 400.

This is entangled with audit finding 9: `AuthExceptionHandler:117` and
`GlobalExceptionHandler:79` both declare `@ExceptionHandler(RuntimeException.class)` and
neither carries `@Order`. Spring picks the first *advice* that has a matching method, so
whichever sorts first wins for the whole exception. It currently happens to resolve in
`GlobalExceptionHandler`'s favour, which is why the orders error-code tests pass — but that is
luck, not design, and a new specific handler is only reliable once the order is pinned.

**Fixes:** `ProfileControllerIT.uploadAvatar_InvalidFileType_Returns400`, and audit finding 9.

**Files:**
- `src/main/java/com/enterprise/ordersuite/api/errors/GlobalExceptionHandler.java`
- `src/main/java/com/enterprise/ordersuite/api/errors/AuthExceptionHandler.java`

- [ ] **Step 1:** Add `@ExceptionHandler(InvalidAvatarException.class)` to
  `GlobalExceptionHandler`, returning `400` with code `INVALID_AVATAR` and the exception's
  message, following the shape of `handleInvalidStatusTransition` exactly.
- [ ] **Step 2:** Add the same `instanceof` branch to the `findRootCause` dispatch inside
  `handleRuntimeException`, so a wrapped `InvalidAvatarException` is still a 400.
  `uploadAvatar` is `@Transactional`, so wrapping is reachable.
- [ ] **Step 3:** Pin advice order — `@Order(1)` on `GlobalExceptionHandler` (specific domain
  handlers) and `@Order(2)` on `AuthExceptionHandler`. Add a one-line comment on each saying
  why, since an unordered pair is what caused this.
- [ ] **Step 4:** Add an IT asserting the **body**, not just the status: the audit's "suspected"
  list notes that no test anywhere asserts a 403/400 body, so a regression to Boot's default
  `{timestamp,status,error,path}` shape would be invisible. Assert `$.code` is
  `INVALID_AVATAR` on the avatar 400.
- [ ] **Step 5:** `./gradlew test --tests "*ProfileControllerIT" --tests "*OrderControllerIT"` —
  the orders error-code assertions are the regression net for the ordering change.

### Task 4: `S3ObjectStorageServiceIT` asserts the wrong exception type

**Defect: test.** After `delete(key)`, the test asserts `headObject` throws
`SdkClientException`. A missing key raises `NoSuchKeyException`, which descends from
`S3Exception → AwsServiceException → SdkServiceException` — a *sibling* of
`SdkClientException`, not a subtype. The production delete works correctly.

**Fixes:** `S3ObjectStorageServiceIT.delete_ExistingObject_RemovesObjectFromMinio`

**Files:** `src/test/java/com/enterprise/ordersuite/storage/S3ObjectStorageServiceIT.java`

- [ ] **Step 1:** Change the assertion at line ~109 to `.isInstanceOf(NoSuchKeyException.class)`,
  adjust the import, and attach `.as("the object must be gone from the bucket after delete")`.
- [ ] **Step 2:** `./gradlew test --tests "*S3ObjectStorageServiceIT"`.

### Task 5: Phase A verification

- [ ] Run `./gradlew test`. **Expect a genuinely green 185/185** — this is the first plan in
  this repo that can claim that. Record the real summary line. Update the
  `avatar-tests-pre-existing-failures` memory, whose baseline this obsoletes.
- [ ] Commit each task separately, message shape `fix(profile): ...` / `test(storage): ...`.

---

# Phase B — audit follow-ups

### Task 6: Make non-admin list scoping fail closed

**Audit finding 2 (MEDIUM).** `OrderRepository.searchOrders`'s
`(:customerId IS NULL OR o.customerId = :customerId)` means a null `customerId` returns
**every order in the database**. The non-admin branches at `OrderService:99` and `:109` pass
`currentUserService.getUserId()` into exactly that parameter, so the whole cross-tenant
boundary rests on that value never being null. Not exploitable today — `CurrentUserService`
throws on an anonymous principal — but it is one refactor away from a full tenant leak.

**Files:** `OrderRepository.java`, `OrderService.java`

- [ ] Add `Page<Order> findByCustomerId(Long customerId, Pageable pageable);` — a derived query
  that cannot degrade to "all rows".
- [ ] Point `getAllOrders`'s non-admin branch at it. For `searchOrders`, keep the flexible query
  for the admin path but make the non-admin path unable to pass null — the simplest correct
  form is to keep the override and add `Objects.requireNonNull(effectiveCustomerId)`.
- [ ] No response changes anywhere. `OrderControllerIT`'s scoping tests are the proof.

### Task 7: Narrow the orders endpoints to `hasAnyRole('USER','ADMIN')`

**Audit finding 3.** `isAuthenticated()` on the five non-DELETE endpoints admits any *future*
role. The restaurant-ops migration adds staff roles, and under `isAuthenticated()` each would
silently gain order access on creation. With the hierarchy, `hasAnyRole('USER','ADMIN')` is
exactly today's behaviour for all three existing roles, so the frozen matrix is unaffected.

**Files:** `OrderController.java`

- [ ] Replace the five `@PreAuthorize("isAuthenticated()")` with
  `@PreAuthorize("hasAnyRole('USER','ADMIN')")`. Leave DELETE's `hasRole('ADMIN')` alone.
- [ ] `OrderControllerIT` must stay green unchanged — that is the evidence the matrix held.

### Task 8: Close the phantom-stock hole and derive price server-side

**Audit finding 6 (MEDIUM/HIGH).** Two linked defects in `OrderService`:

1. `updateOrder:140-150` replaces an order's items **without** calling
   `productService.decrementStock`, while `handleStatusTransition:158-163` increments stock for
   every item on cancel. A user can `PUT` their own PENDING order with
   `{quantity: 1000}` — `transitionTo` returns early on an equal status but the replacement
   block still runs — then cancel it, minting 1000 phantom units.
2. `calculateTotalAmount:200-207` multiplies the **client-supplied**
   `OrderItemRequest.unitPrice` and never consults `Product.price`, so a user sets their own
   order total.

**Files:** `OrderService.java`, `orders.application.service.ProductService` (the consuming-side
interface) and its `products` implementation if a price lookup is not already exposed;
`OrderControllerIT` / `OrderServiceTest`.

- [ ] **Step 1:** Invoke `backend-module-development` — it covers server-side derivation of
  monetary values, and cross-module access must go through the orders-side `ProductService`
  interface, never by importing `products` directly.
- [ ] **Step 2:** Make item replacement symmetric with creation: increment stock back for the
  items being removed, then decrement for the items being added, so every path through
  `updateOrder` leaves stock consistent. Reuse the create path rather than writing a parallel
  one.
- [ ] **Step 3:** Derive `unitPrice` from the product on both create and update. Decide
  explicitly whether `OrderItemRequest.unitPrice` becomes ignored or rejected, and record the
  choice — `api-contract-sync` governs this, since the frontend currently sends it.
- [ ] **Step 4:** Tests first, and they must fail before the fix: a phantom-stock regression
  test (replace items, cancel, assert stock is unchanged) and a price-tampering test (submit a
  wrong `unitPrice`, assert the total uses `Product.price`).

### Task 9: Make the root super admin env-driven

**Audit finding 8 (MEDIUM).** Two separate problems, both currently hardcoded:

- `V15__seed_super_admin.sql:10-25` commits a real bcrypt hash and email, seeded into every
  environment the migration runs in.
- `UserAdminService:29` hardcodes the same address as `ROOT_SUPER_ADMIN_EMAIL`, an anti-lockout
  guard used at lines 65, 141 and 305 to forbid deactivating, re-roling or re-emailing the root
  account. This is a **business rule**, not seed data, and it must stay working.

**Files:** `V15__seed_super_admin.sql`, `UserAdminService.java`, `application.yml`, `.env`,
`.env.example`, plus a new seeder component.

- [ ] **Step 1:** Invoke `flyway-migrations` and `spring-security-changes`.
- [ ] **Step 2:** **Keep V15's role INSERT.** Six IT classes resolve
  `roleRepository.findByName("SUPER_ADMIN")` and will fail without the role row. Remove only
  the user INSERT.
- [ ] **Step 3:** Replace it with an idempotent boot-time seeder that reads
  `SUPER_ADMIN_EMAIL` and `SUPER_ADMIN_PASSWORD_HASH` from the environment and no-ops when
  either is absent, so tests and fresh clones need no secret. Follow the `${VAR}` pattern in
  `application.yml`; extend `.env` and `.env.example` in the same change.
- [ ] **Step 4:** Make `ROOT_SUPER_ADMIN_EMAIL` a configuration property bound to the same
  `SUPER_ADMIN_EMAIL` value. Cover all three guard sites; `AdminUserUpdateControllerIT` and
  `AdminUsersControllerIT` exercise them.
- [ ] **Step 5:** Editing V15 changes its checksum, so the user's **existing local database
  will fail Flyway validation on next boot.** Tests are unaffected (Testcontainers starts
  empty). Tell the user they need `./gradlew flywayRepair`, or to drop the local schema.
- [ ] **Step 6:** Remind the user to rotate the exposed password. The hash stays in git history
  regardless of this change, so rotation is the step that actually remediates it.

### Task 10: Final verification

- [ ] `./gradlew clean test` — full green.
- [ ] Dispatch `spring-security-reviewer` over `4b3535c..HEAD`, asking specifically whether
  Tasks 6, 7 and 9 changed any permission beyond the intended narrowing.
- [ ] Record a status block at the top of this file, matching the convention in the other two
  plans: task table, commit range, deviations, and anything left outstanding.

---

## Notes for the executor

- **Three of the five failures are production bugs.** Do not make them pass by weakening an
  assertion. Only Task 4 is a genuine test defect.
- **Task 3 changes global error handling.** Every `$.code` assertion in the suite is its
  regression net; if one moves, the advice ordering is wrong.
- **Task 8 changes observable order totals** if any client sent a `unitPrice` disagreeing with
  the product. That is intended and was approved, but it is the one place in this plan where
  the frontend can notice.
- `isOrderOwner` is still scheduled for deletion at the restaurant-ops migration. Do not invest
  in generalising it here.
