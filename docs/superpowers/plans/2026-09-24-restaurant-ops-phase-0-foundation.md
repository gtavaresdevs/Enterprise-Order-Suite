# Restaurant-ops Phase 0 — Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Land Phase 0 of the restaurant-ops migration: every persisted timestamp becomes an `Instant` on a `timestamptz` column (D15), item edits on a closed order are rejected with 409 (D13), and the contract manifest records decisions D9–D15 as version 0.3.0.

**Architecture:** Three independent deliverables, in this order. (1) A repository-wide type conversion, `LocalDateTime` → `Instant`, plus migration `V20`, which states per column the zone the existing naive values were written in. (2) A guard in `OrderService.updateOrder` that reuses the existing open/closed notion (`PENDING`/`PROCESSING` are open) and throws a new `OrderNotEditableException`, which `GlobalExceptionHandler` maps to 409 `ORDER_NOT_EDITABLE`. (3) A patch to the canonical OpenAPI manifest in the frontend repository, left **uncommitted** there, and a byte-identical re-copy of it into `docs/contracts/`.

**Tech Stack:** Java 17, Spring Boot 3.5.10 (Hibernate 6.6), PostgreSQL 16 through Flyway, JUnit 5, Mockito, AssertJ, Testcontainers (`postgres:16-alpine`), Gradle.

**Spec:** `docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md`. The parent design is `docs/superpowers/specs/2026-09-20-claude-tooling-and-restaurant-ops-migration-design.md`. Read the Phase 0 spec before starting any task.

## Global Constraints

- The next migration is **`V20`**. Confirm this with `ls src/main/resources/db/migration | sort -V | tail -1`, which should print `V19__Add_Avatar_Key_To_User_Profile.sql`. Never edit V1–V19.
- An entity change and the migration it needs go in the **same commit** (`flyway-migrations` skill).
- Indentation: 4 spaces in `src/main`, 2 spaces in `src/test`.
- Tests: `*Test` has no Spring context. `*IT` uses `@IntegrationTest`. Never use a bare `@SpringBootTest`. Use AssertJ, and name tests `method_scenario_expectedOutcome` (`writing-backend-tests` skill).
- Authorization is expressed in `@PreAuthorize` only. This plan changes **no** permission, role or `@PreAuthorize` expression.
- Do not touch the advice ordering: `AuthExceptionHandler` stays `@Order(1)` with no catch-all, and `GlobalExceptionHandler` stays `@Order(2)` and owns the `RuntimeException` fallback.
- Error codes are `SCREAMING_SNAKE` (D14).
- Loosening an assertion to make a test pass is a finding, not a fix. Stop and report it. (Spec, *Testing*: "any test that needs changing is a finding to report, not a chore to absorb.")
- The frontend repository (`../enterprise-order-suite-frontend`) is edited in place and **never committed, staged or pushed**. It is public, it is on branch `Claude-Assisted-Development`, and it carries unrelated uncommitted work.
- Code, comments, commit messages and docs are written in English.
- Every commit message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Nothing is claimed complete without a full `./gradlew test` (Docker running) and its real summary line. The baseline is **213 tests, 0 failures**.
- Worktrees, if used, go at `C:\Users\Gabriel\eosb` (`git worktree add /c/Users/Gabriel/eosb <ref>`), with `.env` copied in. A path under the scratchpad fails with "Filename too long".

## Review Focus

These are the five failure modes most likely to hurt a real user that no spec-listed test catches. Each one has a test in its owning task.

1. **Existing rows shift by three hours during `V20`.** The naive values came from two different writers (see Task 1, *Finding*). A single `AT TIME ZONE` clause for every column moves one group by 3 h, and nothing fails. The expected behaviour is that each existing value keeps the instant it was written at. Pinned by `V20TimestampConversionIT` (Task 1).
2. **The host JVM is not in UTC.** A developer machine runs in Brasília. The stored `expires_at` has to equal the instant the service computed, and expiry has to compare instants rather than wall-clock readings. Pinned by `RefreshTokenPersistenceIT` (Task 1).
3. **One request both replaces items and moves an open order to a closed state** (`PROCESSING` → `SHIPPED` with `items`). Editability is judged on the order's state *before* the request, so this is accepted and stock settles while the order is still open. Pinned by `updateOrder_replacingItemsWhileShippingAProcessingOrder_isAccepted` (Task 2).
4. **Items on a closed order that reference a product that does not exist.** The request is rejected with 409 before any product is looked up. It does not return 400 `PRODUCT_NOT_FOUND`, and it makes no product-service call. Pinned by `assertReplacingItemsIsRejected`, which uses product `999` and asserts `verifyNoInteractions(productService)` (Task 2).
5. **A rejected request that also carries a status change** (a `SHIPPED` order, `status: DELIVERED`, with `items`). The request writes no `OrderHistory` row, sends no notification and saves nothing. Pinned by the `SHIPPED` → `DELIVERED` case of `assertReplacingItemsIsRejected` (Task 2).

---

## Deviations from the spec, decided in this plan

Read these before executing. The spec is the design; the points below are where reading the code showed that it was incomplete.

- **`V20` names two zones, not one.** The spec's *Risks* section assumes Brasília for every existing naive value. That assumption does not hold. `Clock` is `Clock.systemUTC()` (`config/ApplicationConfig.java:13`), so every column the auth services fill from `LocalDateTime.now(clock)` holds **UTC** wall-clock values. Columns filled by Hibernate's `@CreationTimestamp`/`@UpdateTimestamp`, or by a database `DEFAULT NOW()`, hold **JVM-default (Brasília)** values. Task 1 Step 1 checks this against the local database before the migration is written.
- **The column count is 26, not 32.** 21 columns are naive and 5 are already `timestamptz` (`order_items` ×2, `order_history` ×3). The spec's figure was an estimate.
- **Existing unit tests are replaced, not loosened.** `updateOrder_replacingItemsOn{AShipped,ADelivered,ACancelled}Order_movesNoStock` assert that the call succeeds without moving stock. Under D13 the same call throws. They are replaced by `..._throwsOrderNotEditable` tests that assert `verifyNoInteractions(productService)`. That assertion is strictly stronger: no stock moves and nothing else happens.
- **The manifest patch covers id *references* and path parameters, not only schema ids.** `OrderLine.menuItemId`, `Order.table` and the `{id}` path parameters of int64-keyed resources become `integer`/`int64` along with the ids they refer to. Leaving them as strings would contradict D9 inside the same file. `/public/orders/{id}` stays a string and is documented as taking the `orderNumber`, following D9's "`orderNumber` … the `/track-order` lookup". `SizeOption.id` and `AddonOption.id` are **not** changed: they are sub-objects of a menu item whose storage shape is Phase 2's to decide.

---

### Task 1: Every persisted timestamp is an instant (D15)

**Governing skills:** `flyway-migrations` for the migration and the entities, and `spring-security-changes` for the auth entities and services. The questions that skill requires are answered by the approved spec. Approving this plan approves these answers:

| Question | Answer |
|---|---|
| Who may call it / who is denied? | Unchanged. No endpoint, role or `@PreAuthorize` changes. |
| Is the resource owned? | Unchanged. |
| Widening, narrowing or defect fix? | A defect fix: token expiry is compared in a stated zone instead of an unstated one. |
| What does failure look like? | Unchanged. Same exceptions and status codes. |
| Rate limiting? | Unaffected. |

**Finding: the provenance of existing naive values** (this drives `V20`):

| Written in | How | Columns |
|---|---|---|
| JVM default (`America/Sao_Paulo`) | `BaseEntity` `@CreationTimestamp`/`@UpdateTimestamp`; `DEFAULT NOW()`/`CURRENT_TIMESTAMP` (the JDBC driver sets the session `TimeZone` from the JVM default) | `roles`, `users`, `refresh_tokens`, `identity_audit_events`, `orders`, `products` and `user_profiles` `.created_at`/`.updated_at`; `password_reset_tokens.created_at` |
| UTC | `LocalDateTime.now(clock)` with `Clock.systemUTC()` | `refresh_tokens.expires_at`/`used_at`/`revoked_at`; `password_reset_tokens.expires_at`/`used_at`; `password_history.created_at` |

**Files:**
- Create: `src/main/resources/db/migration/V20__Timestamps_With_Time_Zone.sql`
- Modify: `src/main/java/com/enterprise/ordersuite/common/persistence/BaseEntity.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/domain/{RefreshToken,PasswordResetToken,PasswordHistory}.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenRepository.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/{RefreshTokenService,RefreshTokenCleanupService,PasswordResetService}.java`
- Modify: `src/main/java/com/enterprise/ordersuite/identity/api/dto/{AdminCreateUserResponse,AdminUpdateUserResponse,IdentityAuditEventResponse,MeResponse,UpdateMeResponse,UserDetailResponse,UserSummaryResponse}.java`, `orders/api/dto/OrderResponse.java`, `products/api/dto/ProductResponse.java`, `profile/api/dto/ProfileResponse.java`
- Modify (tests): `src/test/java/com/enterprise/ordersuite/auth/service/{RefreshTokenServiceTest,RefreshTokenCleanupServiceTest,PasswordResetServiceTest}.java`, `identity/api/UsersControllerIT.java`, `orders/api/OrderControllerIT.java`
- Create (tests): `src/test/java/com/enterprise/ordersuite/migration/TimestampColumnsIT.java`, `src/test/java/com/enterprise/ordersuite/migration/V20TimestampConversionIT.java`, `src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `BaseEntity.getCreatedAt()`/`getUpdatedAt()` return `java.time.Instant`. `RefreshToken.isExpired(Instant)` and `isActive(Instant)`. `RefreshTokenRepository.deleteExpired(Instant)` and `deleteUsedOrRevokedBefore(Instant)`. `RefreshTokenService.IssuedRefreshToken(String rawToken, Instant expiresAt)`. Task 2's tests compile against these types without touching them.

- [ ] **Step 1: Confirm the two-writer finding against the local database**

Tokens are issued with `expires_at = now + 14 days` (UTC), and `created_at` is filled from the JVM zone. If the finding holds, the two values differ by the JVM's offset (3 h in Brasília):

```bash
set -a; . ./.env; set +a
PGPASSWORD="$DB_PASSWORD" psql -h "${DB_HOST:-localhost}" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "$DB_NAME" -c \
  "select id, created_at, expires_at - interval '14 days' as issued_utc,
          (expires_at - interval '14 days') - created_at as offset
   from refresh_tokens order by id desc limit 5;"
```

Expected: `offset` is `03:00:00` on every row, give or take milliseconds. If it is `00:00:00`, that JVM ran in UTC: change every `'America/Sao_Paulo'` in Step 6 to `'UTC'`, and update the comment. Any other offset (or no rows at all) means **stop and ask Gabriel** which zone applies. If `psql` is not installed, ask Gabriel to run the query and paste the output. Do not guess.

- [ ] **Step 2: Write the schema guard test**

Create `src/test/java/com/enterprise/ordersuite/migration/TimestampColumnsIT.java`:

```java
package com.enterprise.ordersuite.migration;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TimestampColumnsIT {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  // ddl-auto: validate accepts an Instant field on a TIMESTAMP WITHOUT TIME ZONE column, so
  // Hibernate will not catch a naive column added by a later migration. This test does.
  @Test
  void schema_everyTimestampColumn_carriesItsTimeZone() {
    List<String> naiveColumns = jdbcTemplate.queryForList("""
        select table_name || '.' || column_name
        from information_schema.columns
        where table_schema = 'public'
          and data_type = 'timestamp without time zone'
          and table_name <> 'flyway_schema_history'
        order by 1
        """, String.class);

    assertThat(naiveColumns)
      .as("D15: every persisted timestamp is an instant on a timestamptz column - a naive "
        + "column is read in whatever zone the JVM happens to be in")
      .isEmpty();
  }
}
```

- [ ] **Step 3: Pin the wire format**

In `src/test/java/com/enterprise/ordersuite/identity/api/UsersControllerIT.java`, add `import static org.hamcrest.Matchers.endsWith;` and change `getUser_admin_returns200_andSafeDetail`:

```java
      .andExpect(jsonPath("$.createdAt").exists())
      .andExpect(jsonPath("$.updatedAt").exists())
```

to

```java
      // D15: an instant, serialized in UTC. The frontend's new Date(...) reads a string
      // without a zone as browser-local time, so the suffix is the contract.
      .andExpect(jsonPath("$.createdAt").value(endsWith("Z")))
      .andExpect(jsonPath("$.updatedAt").value(endsWith("Z")))
```

In `src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java`, add the same import. In `getOrderById_asOwner_returns200`, after `.andExpect(jsonPath("$.orderNumber").value(orderNumber))` (and before the closing `;`), add:

```java
      .andExpect(jsonPath("$.createdAt").value(endsWith("Z")))
```

- [ ] **Step 4: Run the new tests and confirm that they fail**

Run: `./gradlew test --tests "com.enterprise.ordersuite.migration.TimestampColumnsIT" --tests "com.enterprise.ordersuite.identity.api.UsersControllerIT" --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT.getOrderById_asOwner_returns200"`

Expected: 3 failures. `TimestampColumnsIT` lists all 21 naive columns. The two `endsWith("Z")` checks fail on a value such as `2026-09-24T21:14:03.123456`. Keep the output for the report.

- [ ] **Step 5: Write the migration conversion test**

This test cannot use `@IntegrationTest`: it has to stop Flyway at V19, plant naive rows, and then apply V20. It starts its own container and never touches the Spring context.

Create `src/test/java/com/enterprise/ordersuite/migration/V20TimestampConversionIT.java`:

```java
package com.enterprise.ordersuite.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Not @IntegrationTest: this has to stop Flyway at V19, plant rows the way the pre-V20 code
// wrote them, and only then apply V20. Its own container, no Spring context.
class V20TimestampConversionIT {

  // 21:00 in Brasilia (UTC-3, no DST since 2019) is midnight UTC.
  private static final Instant NINE_PM_BRASILIA = Instant.parse("2026-09-25T00:00:00Z");

  private static PostgreSQLContainer<?> postgres;

  @BeforeAll
  static void start() {
    postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    postgres.start();
  }

  @AfterAll
  static void stop() {
    postgres.stop();
  }

  @Test
  void v20_convertsEachColumnFromTheZoneItWasWrittenIn() throws Exception {
    migrateTo("19");

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      // Written by Hibernate's @CreationTimestamp: JVM-default (Brasilia) wall-clock time.
      statement.execute("""
          insert into users (first_name, last_name, email, password, role_id, created_at, updated_at)
          values ('V20', 'Probe', 'v20-probe@test.com', 'x',
                  (select id from roles where name = 'USER'),
                  '2026-09-24 21:00:00', '2026-09-24 21:00:00')
          """);
      // Written from LocalDateTime.now(clock) with Clock.systemUTC(): UTC wall-clock time.
      statement.execute("""
          insert into refresh_tokens (user_id, token_hash, created_at, updated_at, expires_at)
          values ((select id from users where email = 'v20-probe@test.com'),
                  repeat('a', 64), '2026-09-24 21:00:00', '2026-09-24 21:00:00',
                  '2026-09-25 00:00:00')
          """);
      statement.execute("""
          insert into password_history (user_id, password_hash, created_at)
          values ((select id from users where email = 'v20-probe@test.com'), 'x',
                  '2026-09-25 00:00:00')
          """);
    }

    migrateTo("20");

    assertThat(instantOf("select created_at from users where email = 'v20-probe@test.com'"))
      .as("a Hibernate-stamped column was written in Brasilia and must keep that instant")
      .isEqualTo(NINE_PM_BRASILIA);
    assertThat(instantOf("select created_at from refresh_tokens where token_hash = repeat('a', 64)"))
      .as("refresh_tokens.created_at comes from BaseEntity, so it is Brasilia too")
      .isEqualTo(NINE_PM_BRASILIA);
    assertThat(instantOf("select expires_at from refresh_tokens where token_hash = repeat('a', 64)"))
      .as("expires_at was written from the UTC clock - reading it as Brasilia would push "
        + "every live token's expiry 3 hours later")
      .isEqualTo(NINE_PM_BRASILIA);
    assertThat(instantOf("select created_at from password_history limit 1"))
      .as("password_history.created_at was written from the UTC clock")
      .isEqualTo(NINE_PM_BRASILIA);
  }

  private static void migrateTo(String version) {
    Flyway.configure()
      .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
      .locations("classpath:db/migration")
      .target(version)
      .load()
      .migrate();
  }

  private static Connection connect() throws Exception {
    return DriverManager.getConnection(
      postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
  }

  private static Instant instantOf(String sql) throws Exception {
    try (Connection connection = connect();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(sql)) {
      assertThat(resultSet.next()).as("query returned no row: " + sql).isTrue();
      return resultSet.getObject(1, OffsetDateTime.class).toInstant();
    }
  }
}
```

Run: `./gradlew test --tests "com.enterprise.ordersuite.migration.V20TimestampConversionIT"`
Expected: FAIL. Flyway reports that target version 20 does not exist, or `getObject(..., OffsetDateTime.class)` fails on a naive column. Either one shows that the test cannot pass without V20.

- [ ] **Step 6: Write `V20`**

Create `src/main/resources/db/migration/V20__Timestamps_With_Time_Zone.sql`:

```sql
-- D15 (docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md):
-- every persisted timestamp is an instant. Converts every remaining
-- TIMESTAMP WITHOUT TIME ZONE column to TIMESTAMP WITH TIME ZONE.
--
-- A naive value carries no zone, so this migration has to say which zone each column was
-- written in. Existing rows came from two writers that did not agree:
--
--   America/Sao_Paulo - columns filled by Hibernate's @CreationTimestamp/@UpdateTimestamp
--     (BaseEntity) or by a column default (NOW(), CURRENT_TIMESTAMP). Both follow the JVM's
--     default zone: Hibernate directly, the defaults through the session TimeZone the JDBC
--     driver sets from it. Every environment that ran the pre-V20 code did so in Brasilia;
--     for the local database this was checked on refresh_tokens before writing this file.
--
--   UTC - columns the auth services filled from LocalDateTime.now(clock), where the Clock
--     bean is Clock.systemUTC().
--
-- Getting a column's zone wrong does not fail. It silently shifts every existing row by
-- three hours - for expires_at, that moves every live token's expiry.
--
-- order_items and order_history were created WITH TIME ZONE and need nothing.

ALTER TABLE roles
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE identity_audit_events
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE orders
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE products
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE user_profiles
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo';

ALTER TABLE refresh_tokens
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMP WITH TIME ZONE USING used_at    AT TIME ZONE 'UTC',
    ALTER COLUMN revoked_at TYPE TIMESTAMP WITH TIME ZONE USING revoked_at AT TIME ZONE 'UTC';

ALTER TABLE password_reset_tokens
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'America/Sao_Paulo',
    ALTER COLUMN expires_at TYPE TIMESTAMP WITH TIME ZONE USING expires_at AT TIME ZONE 'UTC',
    ALTER COLUMN used_at    TYPE TIMESTAMP WITH TIME ZONE USING used_at    AT TIME ZONE 'UTC';

ALTER TABLE password_history
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC';
```

That is 21 columns: 15 in Brasília and 6 in UTC. Check the count against Task 1's *Finding* table before moving on.

- [ ] **Step 7: Convert the entities, repository, services and DTOs**

None of these main files imports `Instant` yet, so a whole-word replacement is safe:

```bash
B=src/main/java/com/enterprise/ordersuite
for f in \
  $B/common/persistence/BaseEntity.java \
  $B/auth/domain/RefreshToken.java $B/auth/domain/PasswordResetToken.java $B/auth/domain/PasswordHistory.java \
  $B/auth/persistence/RefreshTokenRepository.java \
  $B/auth/service/RefreshTokenService.java $B/auth/service/RefreshTokenCleanupService.java $B/auth/service/PasswordResetService.java \
  $B/identity/api/dto/AdminCreateUserResponse.java $B/identity/api/dto/AdminUpdateUserResponse.java \
  $B/identity/api/dto/IdentityAuditEventResponse.java $B/identity/api/dto/MeResponse.java \
  $B/identity/api/dto/UpdateMeResponse.java $B/identity/api/dto/UserDetailResponse.java \
  $B/identity/api/dto/UserSummaryResponse.java $B/orders/api/dto/OrderResponse.java \
  $B/products/api/dto/ProductResponse.java $B/profile/api/dto/ProfileResponse.java
do
  sed -i 's/\bLocalDateTime\b/Instant/g' "$f"
done
grep -rn "LocalDateTime" src/main   # expected: no output
```

This turns `LocalDateTime.now(clock)` into `Instant.now(clock)`, and `Instant` has `isAfter`, `isBefore`, `plus(Duration)` and `minus(Duration)`, so `RefreshToken`, `RefreshTokenService` (`now.plus(refreshTtl)`) and `RefreshTokenCleanupService` (`now.minus(usedRevokedRetention)`) need nothing more. One call site has no `Instant` equivalent. In `PasswordResetService.java` (around line 209), change:

```java
    Instant expiresAt = now.plusMinutes(EXPIRY_MINUTES);
```

to

```java
    Instant expiresAt = now.plus(Duration.ofMinutes(EXPIRY_MINUTES));
```

and add `import java.time.Duration;` next to `import java.time.Clock;`.

Services still take the injected `Clock`. The conversion changes the type, not where time comes from.

- [ ] **Step 8: Convert the three auth unit tests**

These files already import `java.time.Instant` (or `java.time.*`), so delete the `LocalDateTime` import rather than renaming it:

```bash
T=src/test/java/com/enterprise/ordersuite/auth/service
sed -i '/^import java\.time\.LocalDateTime;$/d' $T/RefreshTokenCleanupServiceTest.java $T/PasswordResetServiceTest.java
sed -i 's/\bLocalDateTime\b/Instant/g' $T/RefreshTokenServiceTest.java $T/PasswordResetServiceTest.java
sed -i -e 's/\.plusMinutes(\([0-9]*\))/.plus(Duration.ofMinutes(\1))/g' \
       -e 's/\.minusMinutes(\([0-9]*\))/.minus(Duration.ofMinutes(\1))/g' \
       -e 's/\.minusDays(\([0-9]*\))/.minus(Duration.ofDays(\1))/g' $T/PasswordResetServiceTest.java
sed -i 's/^import java\.time\.Clock;$/import java.time.Clock;\nimport java.time.Duration;/' $T/PasswordResetServiceTest.java
```

In `RefreshTokenCleanupServiceTest.java`, replace the expected values by hand. They stay explicit literals, as the test's own comment asks:

```java
    // Verify exact instants were calculated and passed to the repository.
    // We use explicit expected values rather than recalculating `Instant.now(clock)`
    // in the test, ensuring the math in the service is actually verified.
    Instant expectedNow = Instant.parse("2026-01-29T12:00:00Z");
    Instant expectedCutoff = Instant.parse("2026-01-22T12:00:00Z"); // 7 days prior
```

These are the same instants the old `LocalDateTime` literals denoted under the UTC fixed clock. No assertion gets weaker. Confirm with `grep -rn "LocalDateTime" src/test` (expected: no output).

- [ ] **Step 9: Write the refresh-token persistence test**

This is the spec's "a refresh token issued and expired across the conversion still compares correctly". It goes through the real repository and the real `RefreshTokenService`, with the application's UTC clock and the machine's non-UTC JVM zone.

Create `src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java`:

```java
package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.service.RefreshTokenService;
import com.enterprise.ordersuite.auth.service.tokens.TokenHashing;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RefreshTokenPersistenceIT {

  // Far in the past, so no token another test issues can fall on either side of it.
  private static final Instant EXPIRES_AT = Instant.parse("2020-01-28T12:00:00Z");

  @Autowired
  private RefreshTokenRepository refreshTokenRepository;

  @Autowired
  private RefreshTokenService refreshTokenService;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RoleRepository roleRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private Clock clock;

  @Test
  void save_storesExpiresAtAsTheInstantItWasGiven() {
    RefreshToken token = saveToken("raw-" + UUID.randomUUID(), EXPIRES_AT);

    OffsetDateTime stored = jdbcTemplate.queryForObject(
      "select expires_at from refresh_tokens where id = ?", OffsetDateTime.class, token.getId());

    assertThat(stored.toInstant())
      .as("the column must hold the instant itself, not a wall-clock reading in the JVM's zone")
      .isEqualTo(EXPIRES_AT);
  }

  @Test
  void deleteExpired_comparesTheStoredInstant() {
    RefreshToken token = saveToken("raw-" + UUID.randomUUID(), EXPIRES_AT);

    refreshTokenRepository.deleteExpired(EXPIRES_AT.minusSeconds(1));
    assertThat(refreshTokenRepository.findById(token.getId()))
      .as("one second before it expires, the token must survive cleanup")
      .isPresent();

    refreshTokenRepository.deleteExpired(EXPIRES_AT.plusSeconds(1));
    assertThat(refreshTokenRepository.findById(token.getId()))
      .as("one second after it expires, cleanup must remove it")
      .isEmpty();
  }

  // The reason D15 is in Phase 0: expiry was compared in an unstated zone. Around the
  // boundary, a 3-hour disagreement between writer and reader decides the answer.
  @Test
  void getActiveTokenOrNull_honoursExpiryWithinMinutes_notHours() {
    Instant now = clock.instant();
    String expiringSoon = "raw-" + UUID.randomUUID();
    String justExpired = "raw-" + UUID.randomUUID();
    saveToken(expiringSoon, now.plus(Duration.ofMinutes(5)));
    saveToken(justExpired, now.minus(Duration.ofMinutes(5)));

    assertThat(refreshTokenService.getActiveTokenOrNull(expiringSoon))
      .as("a token with five minutes left is active")
      .isNotNull();
    assertThat(refreshTokenService.getActiveTokenOrNull(justExpired))
      .as("a token that expired five minutes ago is not")
      .isNull();
  }

  private RefreshToken saveToken(String rawToken, Instant expiresAt) {
    User user = new User();
    user.setEmail("rt-" + UUID.randomUUID() + "@test.com");
    user.setPassword("not-a-real-hash");
    user.setRole(roleRepository.findByName("USER").orElseThrow());
    user.setActive(true);
    user.setFirstName("Refresh");
    user.setLastName("Token");
    userRepository.save(user);

    RefreshToken token = new RefreshToken();
    token.setUser(user);
    token.setTokenHash(TokenHashing.sha256Hex(rawToken));
    token.setExpiresAt(expiresAt);
    return refreshTokenRepository.save(token);
  }
}
```

- [ ] **Step 10: Run the Task 1 tests, then the full suite**

Run: `./gradlew test --tests "com.enterprise.ordersuite.migration.*" --tests "com.enterprise.ordersuite.auth.*" --tests "com.enterprise.ordersuite.identity.api.UsersControllerIT" --tests "com.enterprise.ordersuite.orders.api.OrderControllerIT"`
Expected: all pass.

Run: `./gradlew test`
Expected: `218 tests, 0 failures` (the 213 baseline, plus `TimestampColumnsIT` ×1, `V20TimestampConversionIT` ×1 and `RefreshTokenPersistenceIT` ×3). Report the real line. A failure in any test this task did not write is a finding: stop.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/db/migration/V20__Timestamps_With_Time_Zone.sql src/main/java src/test/java
git status   # only the files listed under Task 1
git commit -m "$(cat <<'EOF'
feat(persistence): store every timestamp as an instant on timestamptz

D15 of the Phase 0 design. BaseEntity, the auth entities and every
response DTO move from LocalDateTime to Instant, and V20 converts the
21 remaining naive columns. Each column's USING clause names the zone
its rows were written in: Brasilia for Hibernate and column-default
timestamps, UTC for the auth expiry columns written from the UTC clock.

Live responses now serialize timestamps with a Z suffix.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Item edits are accepted only while an order is open (D13)

**Files:**
- Create: `src/main/java/com/enterprise/ordersuite/orders/domain/exception/OrderNotEditableException.java`
- Modify: `src/main/java/com/enterprise/ordersuite/orders/application/service/OrderService.java` (`updateOrder` at about line 125, `addItems`, `removeAllItems`, `holdsSettleableStock` at about line 199)
- Modify: `src/main/java/com/enterprise/ordersuite/api/errors/GlobalExceptionHandler.java`
- Test: `src/test/java/com/enterprise/ordersuite/orders/application/service/OrderServiceTest.java`
- Test: `src/test/java/com/enterprise/ordersuite/orders/api/OrderControllerIT.java`

**Interfaces:**
- Consumes: Task 1's types, compiled as-is. Nothing in this task mentions a timestamp.
- Produces: `OrderNotEditableException(Long orderId, OrderStatus status)`, a `RuntimeException` in `orders.domain.exception`. HTTP 409 with body `code: "ORDER_NOT_EDITABLE"`.

- [ ] **Step 1: Create the exception so the tests compile**

`src/main/java/com/enterprise/ordersuite/orders/domain/exception/OrderNotEditableException.java`:

```java
package com.enterprise.ordersuite.orders.domain.exception;

import com.enterprise.ordersuite.orders.domain.OrderStatus;

public class OrderNotEditableException extends RuntimeException {
    public OrderNotEditableException(Long orderId, OrderStatus status) {
        super(String.format("Order %d is %s; its items can no longer be changed", orderId, status));
    }
}
```

- [ ] **Step 2: Replace the three "moves no stock" unit tests with rejection tests**

In `OrderServiceTest.java`, add these imports:

```java
import com.enterprise.ordersuite.orders.domain.exception.OrderNotEditableException;
```

Delete these four members: `updateOrder_replacingItemsOnAShippedOrder_movesNoStock`, `updateOrder_replacingItemsOnADeliveredOrder_movesNoStock`, the helper `assertReplacingItemsMovesNoStock`, and `updateOrder_replacingItemsOnACancelledOrder_movesNoStock`. Put this in their place:

```java
  @Test
  void updateOrder_replacingItemsOnAShippedOrder_throwsOrderNotEditable() {
    // Also asks for a legal transition, SHIPPED -> DELIVERED: the rejection must win before
    // any of it - history, notification, save - happens.
    assertReplacingItemsIsRejected(OrderStatus.SHIPPED, OrderStatus.DELIVERED);
  }

  @Test
  void updateOrder_replacingItemsOnADeliveredOrder_throwsOrderNotEditable() {
    assertReplacingItemsIsRejected(OrderStatus.DELIVERED, OrderStatus.DELIVERED);
  }

  @Test
  void updateOrder_replacingItemsOnACancelledOrder_throwsOrderNotEditable() {
    assertReplacingItemsIsRejected(OrderStatus.CANCELLED, OrderStatus.CANCELLED);
  }

  // A SHIPPED or DELIVERED order consumed its stock for good and a CANCELLED one gave it back:
  // none of them is open. Replacing their items used to be accepted, and with an empty list
  // it rewrote totalAmount to zero with no history row. These tests used to assert only that
  // no stock moved; now the request is refused before anything is looked up, moved or saved,
  // which covers that and more. Product 999 does not exist: a closed order answers 409, not
  // PRODUCT_NOT_FOUND.
  private void assertReplacingItemsIsRejected(OrderStatus current, OrderStatus requested) {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(current)
      .totalAmount(new BigDecimal("20.00"))
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(requested)
      .items(List.of(OrderItemRequest.builder()
        .productId(999L)
        .quantity(5)
        .unitPrice(new BigDecimal("10.00"))
        .build()))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    assertThatThrownBy(() -> orderService.updateOrder(orderId, request))
      .isInstanceOf(OrderNotEditableException.class);

    assertThat(existingOrder.getItems())
      .as("the stored lines must survive a rejected request")
      .containsExactly(existingItem);

    assertThat(existingOrder.getTotalAmount())
      .as("the silent zeroing of totalAmount is the defect")
      .isEqualByComparingTo("20.00");

    assertThat(existingOrder.getStatus())
      .isEqualTo(current);

    verifyNoInteractions(productService, orderItemMapper, orderHistoryRepository, notificationService);

    verify(orderRepository, never())
      .save(any(Order.class));
  }

  @Test
  void updateOrder_replacingItemsOnAProcessingOrder_isAccepted() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PROCESSING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.PROCESSING)
      .items(List.of(itemRequest))
      .build();

    OrderItem newItem = OrderItem.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(productService.productExists(202L))
      .thenReturn(true);

    when(productService.getPrice(202L))
      .thenReturn(new BigDecimal("15.00"));

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    // A kitchen can still add a drink to an order being prepared.
    assertThat(existingOrder.getItems())
      .containsExactly(newItem);

    assertThat(existingOrder.getTotalAmount())
      .isEqualByComparingTo("45.00");

    verify(productService)
      .incrementStock(101L, 2);

    verify(productService)
      .decrementStock(202L, 3);
  }

  @Test
  void updateOrder_replacingItemsWhileShippingAProcessingOrder_isAccepted() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.PROCESSING)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    OrderItemRequest itemRequest = OrderItemRequest.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    // Editability is judged on the order as it stands before the request. It is open, so the
    // replacement settles stock first and the transition to SHIPPED runs after it.
    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.SHIPPED)
      .items(List.of(itemRequest))
      .build();

    OrderItem newItem = OrderItem.builder()
      .productId(202L)
      .quantity(3)
      .unitPrice(new BigDecimal("15.00"))
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(productService.productExists(202L))
      .thenReturn(true);

    when(productService.getPrice(202L))
      .thenReturn(new BigDecimal("15.00"));

    when(orderItemMapper.toEntity(itemRequest))
      .thenReturn(newItem);

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    orderService.updateOrder(orderId, request);

    assertThat(existingOrder.getStatus())
      .isEqualTo(OrderStatus.SHIPPED);

    assertThat(existingOrder.getItems())
      .containsExactly(newItem);

    verify(productService)
      .incrementStock(101L, 2);

    verify(productService)
      .decrementStock(202L, 3);

    verify(orderHistoryRepository)
      .save(any(OrderHistory.class));
  }

  @Test
  void updateOrder_statusOnlyOnADeliveredOrder_isNotRejected() {
    Long orderId = 1L;

    OrderItem existingItem = OrderItem.builder()
      .productId(101L)
      .quantity(2)
      .unitPrice(new BigDecimal("10.00"))
      .build();

    Order existingOrder = Order.builder()
      .customerId(CURRENT_USER_ID)
      .status(OrderStatus.DELIVERED)
      .items(new ArrayList<>(List.of(existingItem)))
      .build();

    existingOrder.setId(orderId);

    // No items key at all - what every status-only PUT in OrderControllerIT sends.
    OrderUpdateRequest request = OrderUpdateRequest.builder()
      .status(OrderStatus.DELIVERED)
      .build();

    when(orderRepository.findById(orderId))
      .thenReturn(Optional.of(existingOrder));

    when(orderRepository.save(existingOrder))
      .thenReturn(existingOrder);

    when(orderMapper.toResponse(existingOrder))
      .thenReturn(new OrderResponse());

    assertThat(orderService.updateOrder(orderId, request))
      .isPresent();

    assertThat(existingOrder.getItems())
      .containsExactly(existingItem);

    verifyNoInteractions(productService);
  }
```

`Order` is annotated `@Builder` and has a `totalAmount` field (`orders/domain/Order.java:19,33`), so `.totalAmount(...)` compiles.

- [ ] **Step 3: Add the integration tests**

In `OrderControllerIT.java`, add these tests after `updateOrder_replacingItemsThenCancelling_doesNotMintStock`:

```java
  @Test
  void updateOrder_replacingItemsOnADeliveredOrder_returns409AndLeavesTheOrderIntact() throws Exception {
    Long productId = createProduct(
      "Delivered Order Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    Long orderId = createDeliveredOrder(productId);

    // The exact payload from the security audit: it used to wipe the lines and rewrite
    // totalAmount to 0, with no history row.
    OrderUpdateRequest wipeItems = OrderUpdateRequest.builder()
      .status(OrderStatus.DELIVERED)
      .items(List.of())
      .build();

    mockMvc.perform(put("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(wipeItems)))
      .andExpect(status().isConflict())
      .andExpect(jsonPath("$.code").value("ORDER_NOT_EDITABLE"));

    // A status-only assertion would not have caught the original defect. Re-read the order.
    String body = mockMvc.perform(get("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.items.length()").value(1))
      .andReturn()
      .getResponse()
      .getContentAsString();

    assertThat(new BigDecimal(objectMapper.readTree(body).get("totalAmount").asText()))
      .as("the rejected request must not have touched the total")
      .isEqualByComparingTo("20.00");

    assertThat(getProductStock(productId))
      .as("a delivered order's 2 units stay consumed")
      .isEqualTo(8);
  }

  @Test
  void updateOrder_statusOnlyOnADeliveredOrder_stillReturns200() throws Exception {
    Long productId = createProduct(
      "Delivered Status Product",
      "SKU-" + UUID.randomUUID(),
      new BigDecimal("10.00"),
      10
    );

    Long orderId = createDeliveredOrder(productId);

    // Proves the narrowing did not overreach: no items key, no rejection.
    mockMvc.perform(put("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(OrderUpdateRequest.builder()
          .status(OrderStatus.DELIVERED)
          .build())))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status").value("DELIVERED"));
  }
```

Then add these helpers next to `createProduct`:

```java
  // Two units at 10.00, walked through every legal transition to DELIVERED.
  private Long createDeliveredOrder(Long productId) throws Exception {
    OrderCreateRequest createRequest = OrderCreateRequest.builder()
      .orderNumber("ORD-DELIVERED-" + UUID.randomUUID())
      .customerId(adminUser.getId())
      .status(OrderStatus.PENDING)
      .items(List.of(OrderItemRequest.builder()
        .productId(productId)
        .quantity(2)
        .unitPrice(new BigDecimal("10.00"))
        .build()))
      .build();

    String response = mockMvc.perform(post("/orders")
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(createRequest)))
      .andExpect(status().isCreated())
      .andReturn()
      .getResponse()
      .getContentAsString();

    Long orderId = objectMapper.readTree(response)
      .get("id")
      .asLong();

    moveTo(orderId, OrderStatus.PROCESSING);
    moveTo(orderId, OrderStatus.SHIPPED);
    moveTo(orderId, OrderStatus.DELIVERED);

    return orderId;
  }

  private void moveTo(Long orderId, OrderStatus target) throws Exception {
    mockMvc.perform(put("/orders/{id}", orderId)
        .header("Authorization", "Bearer " + adminToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(OrderUpdateRequest.builder()
          .status(target)
          .build())))
      .andExpect(status().isOk());
  }
```

- [ ] **Step 4: Run the tests and confirm that they fail for the right reason**

Run: `./gradlew test --tests "com.enterprise.ordersuite.orders.*"`

Expected failures:
- the three `..._throwsOrderNotEditable` tests: `Expecting code to raise a throwable`
- `updateOrder_replacingItemsOnADeliveredOrder_returns409AndLeavesTheOrderIntact`: `Status expected:<409> but was:<200>`

Expected to pass already: `updateOrder_replacingItemsOnAProcessingOrder_isAccepted`, `updateOrder_replacingItemsWhileShippingAProcessingOrder_isAccepted`, `updateOrder_statusOnlyOnADeliveredOrder_isNotRejected` and `updateOrder_statusOnlyOnADeliveredOrder_stillReturns200`. They guard against overreach, and today's code already behaves this way. Keep the output.

- [ ] **Step 5: Implement the boundary in `OrderService`**

Add the import `com.enterprise.ordersuite.orders.domain.exception.OrderNotEditableException`.

In `updateOrder`, make the check the first statement inside `.map(existingOrder -> {`, **before** `validateProductsExist`:

```java
                .map(existingOrder -> {
                    // Judged on the order as it stands before this request, and before any
                    // product is looked up: a closed order answers 409 whatever the items say.
                    // A request with no items key is a status-only update and is untouched.
                    if (request.getItems() != null && !isOpen(existingOrder)) {
                        throw new OrderNotEditableException(id, existingOrder.getStatus());
                    }

                    validateProductsExist(request.getItems());
```

Rename `holdsSettleableStock` to `isOpen` at its definition and at its two call sites (`addItems`: `boolean moveStock = isOpen(order);`; `removeAllItems`: `if (isOpen(order)) {`). Replace its comment:

```java
    // Whether the order is still open: PENDING or PROCESSING. This is the single notion of
    // an open order in this service, and it has two consequences.
    //
    // Stock: an open order's claim on stock is settleable, because it can still be cancelled
    // and cancelling is what credits stock back. A CANCELLED order already gave its stock
    // back; a SHIPPED or DELIVERED one consumed it for good. Moving stock for either would
    // mint it.
    //
    // Editability: only an open order accepts an item payload (D13). A closed order is a
    // record - replacing its lines used to rewrite totalAmount, to zero for an empty list,
    // with no history row. updateOrder refuses before reaching addItems/removeAllItems, so
    // on that path the stock check above is now a second line of defence.
    //
    // The restaurant-ops rename keeps the boundary: New and Preparing stay open; Ready,
    // Completed and Cancelled do not.
    private boolean isOpen(Order order) {
        OrderStatus status = order.getStatus();
        return status == OrderStatus.PENDING || status == OrderStatus.PROCESSING;
    }
```

- [ ] **Step 6: Map the exception to 409 in `GlobalExceptionHandler`**

Add the import `com.enterprise.ordersuite.orders.domain.exception.OrderNotEditableException`. Add this handler directly after `handleInvalidStatusTransition`:

```java
    // 409, not 400: the payload is well-formed. It is the order's state that refuses it.
    @ExceptionHandler(OrderNotEditableException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderNotEditable(OrderNotEditableException ex) {
        log.warn("OrderNotEditableException: {}", ex.getMessage());
        ApiErrorResponse body = new ApiErrorResponse(
                "ORDER_NOT_EDITABLE",
                ex.getMessage(),
                Instant.now(clock),
                null
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
```

In `handleRuntimeException`, add a root-cause branch after the `InvalidStatusTransitionException` branch, so that a wrapped instance maps the same way as the others:

```java
        if (rootCause instanceof OrderNotEditableException) {
            log.warn("Handling OrderNotEditableException from root cause: {}", rootCause.getMessage());
            return handleOrderNotEditable((OrderNotEditableException) rootCause);
        }
```

Do not add `@Order` or any catch-all, and do not touch `AuthExceptionHandler`.

- [ ] **Step 7: Run the orders tests, then the full suite**

Run: `./gradlew test --tests "com.enterprise.ordersuite.orders.*"`
Expected: all pass. That includes every pre-existing `OrderControllerIT` item-replacement test, and every one of them runs on a `PENDING` order. They must pass **unchanged**: that is the evidence the boundary sits where intended.

Run: `./gradlew test`
Expected: `223 tests, 0 failures`. That is Task 1's 218, plus 3 unit tests (the rejection tests replace three existing ones one for one), plus 2 IT tests. Report the real line.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/orders src/main/java/com/enterprise/ordersuite/api/errors/GlobalExceptionHandler.java src/test/java/com/enterprise/ordersuite/orders
git status   # only the files listed under Task 2
git commit -m "$(cat <<'EOF'
fix(orders): reject item edits on an order that is no longer open

D13 of the Phase 0 design. PUT /orders/{id} with an items payload on a
SHIPPED, DELIVERED or CANCELLED order used to replace the lines and
rewrite totalAmount - to 0 for an empty list - with no history row. It
now returns 409 ORDER_NOT_EDITABLE before anything is looked up or
saved. Status-only updates are unaffected.

holdsSettleableStock becomes isOpen: one notion of an open order,
governing both stock movement and editability.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Record the decisions in the contract (manifest 0.3.0)

**Governing skill:** `api-contract-sync`. Patch the canonical copy only where the decisions touch it and leave every other line byte-identical. Bump `info.version`, add the `x-changelog` entry at the **top**, and never delete a superseded decision.

**Files:**
- Modify (**not committed**): `../enterprise-order-suite-frontend/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml` (below: `$M`)
- Replace: `docs/contracts/backend-integration-manifest.openapi.yaml` (whole-file copy)
- Modify: `docs/contracts/README.md`
- Modify: `docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md` (status line)

**Interfaces:**
- Consumes: the error code `ORDER_NOT_EDITABLE` and 409 from Task 2, and the `Z` wire format from Task 1. The changelog describes both.
- Produces: manifest version `0.3.0`. Phases 1–4 implement against it.

- [ ] **Step 1: Confirm the starting point**

```bash
M=../enterprise-order-suite-frontend/order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml
cmp "$M" docs/contracts/backend-integration-manifest.openapi.yaml && echo IDENTICAL
grep -n '^  version:' "$M"            # expected: 4:  version: "0.2.0"
git -C ../enterprise-order-suite-frontend diff --quiet -- order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml && echo CLEAN
```

Expected: `IDENTICAL` and `CLEAN`. If the canonical file has moved past 0.2.0, **stop**: someone else patched it, and the edits below have to be re-derived against the new version.

- [ ] **Step 2: Re-case the three error codes (D14), without touching the changelog history**

The 0.2.0 changelog entry mentions `categoryInUse`, and history is never rewritten. Limit the substitution to the lines before `x-changelog:`:

```bash
sed -i -e '1,/^x-changelog:/ s/categoryNameRequired/CATEGORY_NAME_REQUIRED/g' \
       -e '1,/^x-changelog:/ s/categoryNameTaken/CATEGORY_NAME_TAKEN/g' \
       -e '1,/^x-changelog:/ s/categoryInUse/CATEGORY_IN_USE/g' "$M"
grep -n 'categoryName\|categoryInUse' "$M"   # expected: only the 0.2.0 changelog line
```

This rewrites line 105 (`Error.code`) and the `/menu-categories` responses at lines 702, 703, 728, 730, 735 and 739.

- [ ] **Step 3: Make the ids int64 (D9)**

Use targeted edits, each anchored on the line above it so it is unique:

1. `info.version`: `  version: "0.2.0"` → `  version: "0.3.0"`.
2. The live baseline, line 62: `        OrderStatus). Ids are int64 live but \`string\` in the frontend types — see x-open-decisions.` → `        OrderStatus). Ids are int64 live and stay int64 in the target (0.3.0, x-open-decisions id-type).`
3. `MenuItem` (anchor `required: [id, name, description, category, price, image, stockQuantity, available]` + `properties:`): `        id: { type: string }` → `        id: { type: integer, format: int64 }`.
4. `Table` (anchor `required: [id, name, qrCodeUrl]`): the same change.
5. `DeliveryZone` (anchor `required: [id, neighborhood, feeAmount, etaMinutes, active]`): the same change.
6. `OrderLine.menuItemId`: change the `type: string` directly under `        menuItemId:` to `type: integer` and add `          format: int64` beneath it. Leave its description unchanged.
7. `Order.table`: change the `type: string` directly under `        table:` (the one whose description is `Table id reference, present only when channel is Dine-in.`) to `type: integer` and add `          format: int64` beneath it.
8. `Order.id`: replace the whole `id:` property (three lines, from `        id:` to the description that ends `just a stable unique id.'`) with:

```yaml
        id:
          type: integer
          format: int64
          description: 'Server-assigned numeric primary key (0.3.0, x-open-decisions id-type). The mock''s `{PREFIX}-{year}-{random}` string was serving as a display and lookup key; that role is now orderNumber.'
        orderNumber:
          type: string
          description: 'Human-readable unique key (e.g. DEL-2026-8123): what staff and customers see, and what the public /track-order lookup takes. Added in 0.3.0; already present on the live OrderResponse.'
```

9. Path parameters. Under `/menu-items/{id}`, `/tables/{id}`, `/orders/{id}/status`, `/delivery-zones/{id}` and `/public/tables/{id}`, the `- name: id` parameter's `        schema: { type: string }` becomes `        schema: { type: integer, format: int64 }`. Each one is the fifth line below its path key.
10. `/public/orders/{id}`: keep `schema: { type: string }` and add this line directly under it, at the same indentation as `schema`:

```yaml
        description: 'The order''s orderNumber (e.g. DEL-2026-8123), not its numeric id - see Order.orderNumber (0.3.0).'
```

`SizeOption.id` and `AddonOption.id` stay `string`. Phase 2 decides how those sub-objects are stored.

- [ ] **Step 4: Replace `Order.createdAt` and add `businessDate` (D10)**

Replace the whole `createdAt:` property of `Order` (two lines: `        createdAt:` / `          type: string` and the long `description:` that starts `'Date-only in the current mock fixture`) with:

```yaml
        createdAt:
          type: string
          format: date-time
          description: 'UTC instant the order was placed, serialized with a Z suffix (0.3.0, x-open-decisions created-at-format). Kept as an instant for the KDS, prep-time measurement and same-day ordering. Do NOT compare it as a date string - Home earnings and Analytics compare businessDate.'
        businessDate:
          type: string
          format: date
          readOnly: true
          description: 'The YYYY-MM-DD restaurant-local date the order belongs to. Stamped by the server once, at creation, in RestaurantSettings.timezone, and never recomputed: editing the timezone affects later orders only. Home earnings and Analytics compare THIS field (===, >, startsWith(YYYY-MM)), not createdAt. Never computed client-side. Added in 0.3.0.'
```

- [ ] **Step 5: Add `RestaurantSettings.timezone` (D10a) and document `MenuCategory` (D11)**

In `RestaurantSettings.properties`, after the `whatsappNumber:` line, add:

```yaml
        timezone:
          type: string
          description: 'IANA zone identifier, e.g. America/Sao_Paulo. Admin-editable. Each order''s businessDate is stamped in this zone at creation, so editing it changes FUTURE orders only. Until it is set, the server falls back to its restaurant.timezone configuration, and it refuses to start if neither is set. Added in 0.3.0.'
```

In `MenuCategory`, between `      type: object` and `      required: [name]`, add:

```yaml
      description: 'Addressed by NAME on the wire - in the /menu-categories/{name} path and in MenuItem.category. Backed internally by a stable id with a foreign key from each menu item (0.3.0, x-open-decisions category-identity), so a rename is a single-row update and CATEGORY_IN_USE is the foreign key refusing a delete. The id is never exposed.'
```

- [ ] **Step 6: Record the two live changes in the live baseline (D15, D13)**

In `info.description`, directly after the line `        has a pending change (tag \`Auth\`).`, add:

```
      - Timestamps (0.3.0): every live timestamp (`createdAt`/`updatedAt` on /me, /me/profile,
        /users, /admin/*, /orders, /products) now serializes as a UTC instant with a `Z` suffix,
        e.g. `2026-09-24T21:14:03.120Z`. It used to be a zone-less local datetime. `new Date(...)`
        reads the old form as browser-local time and the new one as UTC, so a date shown near
        midnight can move by a day. FRONTEND ACTION REQUIRED.
      - Legacy `PUT /orders/{id}` (0.3.0): a payload carrying `items` on a SHIPPED, DELIVERED or
        CANCELLED order returns 409 with code ORDER_NOT_EDITABLE instead of being applied.
        Status-only updates are unaffected.
```

- [ ] **Step 7: Flip the four open decisions**

In `x-open-decisions`, for each of `id-type`, `category-identity`, `created-at-format` and `dev-cookie-secure`, change `    status: open` to `    status: decided-2026-09-24`, keep `text:` byte-identical, and add a `resolution:` line after it:

```yaml
    resolution: 'Backend keeps int64. MenuItem, Table, DeliveryZone and Order expose numeric ids, and references to them (OrderLine.menuItemId, Order.table, the {id} path parameters) follow. The four mock-backed frontend types (menu, orders, tables, settings) change id: string to id: number when their services are wired. Order also exposes orderNumber as its human-readable display and tracking key, which /public/orders/{id} takes. Rejected: opaque public ids - the /public/* defence is phone-scoping, not id opacity.'
```

```yaml
    resolution: 'Normalized: menu_categories(id, name UNIQUE) with a foreign key from each menu item. The wire contract stays name-based (path and MenuItem.category) and the id is never exposed. A rename is a single-row update, and CATEGORY_IN_USE is the foreign key.'
```

```yaml
    resolution: 'Order.createdAt is a UTC instant (date-time). The server also returns Order.businessDate (YYYY-MM-DD), stamped once at creation in RestaurantSettings.timezone and never recomputed; Home and Analytics compare businessDate. The zone falls back to the restaurant.timezone configuration while unset, and the server refuses to start if neither is set - never the host''s system zone.'
```

```yaml
    resolution: 'Env-bound, production-safe defaults: security.refresh-cookie.secure ${REFRESH_COOKIE_SECURE:true}, security.refresh-cookie.same-site ${REFRESH_COOKIE_SAME_SITE:Lax}, security.cors.allowed-origins ${CORS_ALLOWED_ORIGINS:http://localhost:3000}. Local development opts out of Secure explicitly. Not derived from the Spring profile. Built in backend Phase 1.'
```

(These four blocks go, in order, to `id-type`, `category-identity`, `created-at-format` and `dev-cookie-secure`.)

- [ ] **Step 8: Add the changelog entry at the top**

Directly under `x-changelog:`, before `  - version: "0.2.0"`, insert:

```yaml
  - version: "0.3.0"
    date: 2026-09-24
    changes:
      - 'Decided id-type, category-identity, created-at-format and dev-cookie-secure (x-open-decisions, each with a resolution).'
      - 'Ids: MenuItem.id, Table.id, DeliveryZone.id and Order.id, the references OrderLine.menuItemId and Order.table, and the {id} path parameters of /menu-items, /tables, /orders/{id}/status, /delivery-zones and /public/tables are integer (int64). /public/orders/{id} takes the orderNumber.'
      - 'Order: orderNumber added; createdAt is a date-time instant; businessDate added (read-only, server-stamped, what Home and Analytics compare).'
      - 'RestaurantSettings.timezone added (IANA identifier, admin-editable, affects future orders only).'
      - 'MenuCategory: documented the internal stable id behind the name-based contract.'
      - 'Error codes are SCREAMING_SNAKE, matching what the live backend already emits: categoryNameRequired, categoryNameTaken and categoryInUse became CATEGORY_NAME_REQUIRED, CATEGORY_NAME_TAKEN and CATEGORY_IN_USE.'
      - 'LIVE: every live timestamp now carries a Z suffix, and legacy PUT /orders/{id} rejects item edits on a closed order with 409 ORDER_NOT_EDITABLE (see the live baseline in info.description).'
    why: 'Backend migration Phase 0 (enterprise-order-suite docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md, decisions D9-D15). Every later phase''s schema depends on these four decisions.'
    breaking: 'Ids on MenuItem, Table, DeliveryZone and Order change from string to integer. They are mock-backed today, so the frontend types change when each service is wired. Live timestamps gain a Z suffix, which changes how new Date() reads them on endpoints the frontend calls TODAY - this one needs a coordinated frontend change.'
```

- [ ] **Step 9: Validate, and check that only intended lines changed**

```bash
npx --yes @apidevtools/swagger-cli@4.0.4 validate "$M"
git -C ../enterprise-order-suite-frontend diff --stat -- order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml
git -C ../enterprise-order-suite-frontend diff -- order-ui/docs/superpowers/specs/2026-09-14-backend-integration-manifest.openapi.yaml
```

Expected: `... is valid`. The diff contains **only** the edits from Steps 2–8. Read every hunk; any other change breaks the byte-identical rule and has to be reverted. Do **not** run `git add` in the frontend repository.

- [ ] **Step 10: Re-copy the snapshot, and update its README and the spec status**

```bash
cp "$M" docs/contracts/backend-integration-manifest.openapi.yaml
cmp "$M" docs/contracts/backend-integration-manifest.openapi.yaml && echo IDENTICAL
```

In `docs/contracts/README.md`, change the two rows:

```
| Snapshot of version | 0.3.0 (dated 2026-09-24) |
| Taken on | 2026-09-24 |
```

(Use the actual date of execution for *Taken on* if it is later.)

In the spec, change `Status: approved (design); implementation plan to follow` to:

```
Status: implemented — see `../plans/2026-09-24-restaurant-ops-phase-0-foundation.md`
```

- [ ] **Step 11: Commit (backend only)**

```bash
git add docs/contracts/backend-integration-manifest.openapi.yaml docs/contracts/README.md docs/superpowers/specs/2026-09-24-restaurant-ops-phase-0-foundation-design.md
git commit -m "$(cat <<'EOF'
docs(contracts): snapshot manifest 0.3.0 recording Phase 0 decisions

id-type, category-identity, created-at-format and dev-cookie-secure are
decided (D9-D12); error codes are SCREAMING_SNAKE (D14); the live
timestamp Z suffix (D15) and the 409 ORDER_NOT_EDITABLE (D13) are in the
live baseline. The canonical copy is patched in the frontend repository
and left for its owner to commit.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 12: Final verification and hand-off**

Run: `./gradlew test`. Expected: `223 tests, 0 failures`, the same as at the end of Task 2, because this task changes no code. Report the real line.

Tell Gabriel three things:
1. The canonical manifest is modified and **not committed** in `enterprise-order-suite-frontend`, and it is his to review and commit.
2. The `Z` suffix is live on `/me`, `/me/profile`, `/users`, `/admin/*`, `/orders` and `/products` now, and the frontend needs its side (`src/utils/format.ts`, `src/features/profile/hooks/useProfile.ts`).
3. Any environment other than his local database that ran pre-V20 code has to confirm that its JVM ran in Brasília before V20 runs there (Task 1 Step 1's query). Otherwise the `America/Sao_Paulo` columns shift.
