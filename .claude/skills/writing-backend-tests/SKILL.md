---
name: writing-backend-tests
description: Use when writing or modifying any test in this repository - covers the *Test versus *IT split, the real-login integration pattern, naming, AssertJ style, and the mandatory denied-access and public-endpoint leak coverage.
---

# Writing backend tests

Two kinds of test, one naming rule that tells them apart, and a short list of coverage
obligations that are not negotiable. Read a neighbouring test in the same package before
writing a new one — the house style is consistent and you should match it.

## The split

| Suffix | Context | Annotations |
|---|---|---|
| `*Test` | **No Spring context.** Plain JUnit 5 + Mockito. | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` |
| `*IT` | Full context, real Postgres and MinIO containers. | `@IntegrationTest` + `@AutoConfigureMockMvc` |

Precedent: `orders/application/service/OrderServiceTest.java:48` and
`orders/api/OrderControllerIT.java:34-35`.

**Never hand-roll `@SpringBootTest`.** Use the `@IntegrationTest` composite
(`support/IntegrationTest.java`) — it imports `PostgresTestContainerConfig` and
`MinioTestContainerConfig`, and that import is precisely what guarantees a test can never
reach a real database or bucket. A hand-rolled `@SpringBootTest` loses that guarantee
silently.

## Authenticate with a real login, not `@WithMockUser`

The house pattern creates a user through `userRepository`, POSTs to `/auth/login`, pulls
`$.accessToken` out of the response, and sends it as `Bearer`. See
`OrderControllerIT.java:341-371` for `createTestUser` and `loginAndGetAccessToken`.

This matters because it exercises `JwtAuthenticationFilter` and the real authority mapping.
`@WithMockUser` injects an `Authentication` directly and bypasses both — and authority
mapping is exactly where this repository has had defects. A test that mocks the thing under
suspicion proves nothing about it.

`@WithMockUser` is acceptable **only** for tests asserting URL-level rules that never touch
the JWT filter. Existing precedent: `security/ActuatorSecurityIT.java:63`.

## Conventions

- **Naming:** `method_scenario_expectedOutcome` — `getOrderById_notAsOwner_returns403`,
  `updateOrder_invalidTransition_returns400`. `@DisplayName` is optional; when present it is
  a full sentence starting with "Should" (`security/ratelimit/LoginRateLimitIT.java:39`).
- **AssertJ, not JUnit assertions.** `assertThat` / `assertThatThrownBy`. Attach
  `.as("why this must hold")` to any assertion whose failure would otherwise be cryptic —
  see `LoginRateLimitIT.java:48-53`.
- **Unique test data.** Emails are `"prefix-" + UUID.randomUUID() + "@test.com"`
  (`OrderControllerIT.java:69`). Integration tests share one container across the class, so
  fixed values collide across tests.
- **Time.** Inject `Clock` in production code and drive it from `support/MutableClock.java`
  in the test. Never call `Instant.now()` directly in code that needs to be time-tested —
  rate limit windows and token expiry both depend on this.
- **Property overrides** go on the class via `@TestPropertySource`
  (`LoginRateLimitIT.java:23-27`).
- **Indentation is 2 spaces** in `src/test`. (`src/main` is 4 — do not carry one into the other.)

## Mandatory coverage

These are obligations, not suggestions. A change that lacks them is not finished.

1. **Every resource-scoped endpoint gets a denied-path test.** Assert the 403, not only the
   happy path. `getOrderById_notAsOwner_returns403` is the shape.
2. **Every endpoint reachable without authentication gets a leak test.** For the `/public/*`
   surface, once it exists: assert it does not return fields or rows an anonymous caller must
   not see — unavailable ("86'd") menu items, the full table roster, another customer's order.
3. **Every change to role handling gets a `SUPER_ADMIN` case.** `SUPER_ADMIN` inherits
   `ADMIN` only through `RoleHierarchy`, which `methodSecurityExpressionHandler` applies to
   `@PreAuthorize` and **not** to a raw `getAuthorities()` call. That is the live defect in
   `orders/application/service/OrderService.java:209` — a `SUPER_ADMIN` fails
   `equals("ROLE_ADMIN")` and is silently demoted. A test with only `ADMIN` and `USER` would
   pass over it.

## Proving a defect

When a test exists to prove a bug, run it **before** the fix and keep the actual failure
output. A test that has never been observed failing proves only that it is not wired to
anything.

## Running

```bash
./gradlew test                                     # everything — required before any completion claim
./gradlew test --tests "fully.qualified.ClassName" # one class, while iterating
```

Docker must be running; Testcontainers starts real Postgres and MinIO. Report the real
output — never describe a run that did not happen.
