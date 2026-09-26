# Restaurant-ops Phase 1 — Auth Target Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make auth rate limiting actually fire under the production context path, then move the refresh token into an HttpOnly cookie with rotation, family revocation on reuse, `Origin` validation and identity claims in the JWT — without breaking any client that still sends the token in the body.

**Architecture:** Tasks 1–2 fix the rate limiter first, in their own commits: path matching goes through one `RequestPaths.withinApplication` helper (context path stripped), and client-IP trust becomes an explicit env setting. Then `refresh_tokens` gains a `family_id`; `RefreshTokenService` owns issue/rotate/revoke by family; `AuthenticationService.refresh` runs in one transaction with a row lock and commits the family revocation even when it answers 401. The web layer resolves the token from cookie-then-body, emits `Set-Cookie` via a `RefreshCookieFactory`, and a `RefreshOriginFilter` (ahead of `CorsFilter`) rejects cookie-borne requests from unlisted origins.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, Spring Data JPA / Hibernate 6, Flyway, PostgreSQL 16 (Testcontainers), JJWT, JUnit 5, Mockito, AssertJ, MockMvc.

**Spec:** `docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md` (decisions D16–D24; D12 and D14 from the Phase 0 spec).

## Global Constraints

- Invoke the project skills before the matching work: `flyway-migrations` (Task 3), `spring-security-changes` (Tasks 1, 2, 5, 6, 9, 10), `writing-backend-tests` (every task), `api-contract-sync` (Task 9).
- Config keys and defaults, verbatim (D12): `security.refresh-cookie.secure ${REFRESH_COOKIE_SECURE:true}`, `security.refresh-cookie.same-site ${REFRESH_COOKIE_SAME_SITE:Lax}`, `security.cors.allowed-origins ${CORS_ALLOWED_ORIGINS:http://localhost:3000}`. Never derive them from the Spring profile.
- Cookie name `refreshToken`; attributes `HttpOnly`, `Secure`/`SameSite` from properties, `Path=<context-path>/auth`, `Max-Age` = 14 days (1209600 s).
- Error codes are `SCREAMING_SNAKE` (D14): invalid refresh → **401** `INVALID_REFRESH_TOKEN`; bad origin → **403** `ORIGIN_NOT_ALLOWED`. Error body is `ApiErrorResponse`.
- `AuthResponse` keeps both `accessToken` and `refreshToken` in the body — removal is Phase 6.
- Never edit `docs/contracts/backend-integration-manifest.openapi.yaml`.
- Tests: `*Test` = no Spring context; `*IT` = `@IntegrationTest` (never a bare `@SpringBootTest`, except migration ITs that own their container like `V20TimestampConversionIT`). AssertJ only. Unique emails via `UUID.randomUUID()`. `src/test` indents 2 spaces; in `src/main` match the file you edit, new files use 4.
- Authorization stays in `@PreAuthorize`; the Origin check is a CSRF defense in a filter, not authorization.
- Commit message trailer: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Done means a full `./gradlew test` green (Docker running) and the real output read.

## Review Focus

1. **A developer's local `.env` sets `REFRESH_COOKIE_SECURE=false`** (Task 11 tells them to) and spring-dotenv loads it in tests — cookie ITs must pin `security.refresh-cookie.*` and `security.cors.allowed-origins` with `@TestPropertySource`, or they pass or fail depending on the machine. (Task 9, Task 10.)
2. **Production context path `/api`.** MockMvc runs with an empty context path, so ITs never see `/api` unless told to — this is how the rate limiter shipped switched off. Every path match goes through `RequestPaths.withinApplication`, and the cookie `Path` is built from `request.getContextPath()`. Pinned by an IT that runs with `contextPath("/api")` (Task 1) and unit tests for the `/api` case (Task 1 `RequestPathsTest`, Task 9 `RefreshCookieFactoryTest`, Task 10 `RefreshOriginFilterTest`).
3. **`POST /auth/refresh` with `Content-Type: application/json` and an empty body** — what the target frontend sends when it has no cookie. Must answer 401 `INVALID_REFRESH_TOKEN`, not 400. (Task 9 IT.)
4. **The reuse revocation rolled back along with the 401.** If `noRollbackFor` is missing, reuse detection silently does nothing. Guarded by presenting the successor after a reuse. (Task 5 IT.)
5. **Near-miss origins** — `http://localhost:3000/` (trailing slash), `http://localhost:3001`, `null` (sandboxed iframes send the literal string `null`). Exact match only; all must be 403. (Task 10 unit test.)

---

### Task 1: Rate limiting fires under the context path (D22)

**Files:**
- Create: `src/main/java/com/enterprise/ordersuite/security/web/RequestPaths.java`
- Modify: `src/main/java/com/enterprise/ordersuite/security/web/AuthRateLimitFilter.java:68` and `:96`
- Test: `src/test/java/com/enterprise/ordersuite/security/web/RequestPathsTest.java`, `src/test/java/com/enterprise/ordersuite/security/ratelimit/ContextPathRateLimitIT.java`

**Interfaces:**
- Produces: `public static String RequestPaths.withinApplication(HttpServletRequest request)` — the request URI minus the context path. Task 10's `RefreshOriginFilter` uses it.

Background: `AuthRateLimitFilter` compares `getRequestURI()` (which includes the context path) with `"/auth/login"` etc. Under `SERVER_CONTEXT_PATH=/api` nothing matches and the limiter skips every request. MockMvc sends no context path, so the existing rate-limit ITs pass. Confirmed on 2026-09-25 (spec, "Findings").

- [ ] **Step 1: Invoke the `spring-security-changes` skill.** This is a defect fix that narrows nothing and widens nothing: the limits were always meant to apply.

- [ ] **Step 2: Write the failing regression IT**

`src/test/java/com/enterprise/ordersuite/security/ratelimit/ContextPathRateLimitIT.java`:

```java
package com.enterprise.ordersuite.security.ratelimit;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

// Every other rate-limit IT sends requests without a context path, which is how the limiter
// shipped switched off: production serves under SERVER_CONTEXT_PATH=/api, and the filter
// compared the raw request URI (/api/auth/login) with "/auth/login".
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.rate-limit.enabled=true",
  "security.rate-limit.login.capacity=5",
  "security.rate-limit.login.refill-seconds=60"
})
class ContextPathRateLimitIT {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("Should rate limit login when the application is served under the /api context path")
  void login_underTheApiContextPath_isRateLimited() throws Exception {
    List<Integer> statuses = sixFailedLogins("/api", "10.20.30.1");

    assertThat(statuses.subList(0, 5))
      .as("the first five attempts are within capacity")
      .doesNotContain(429);
    assertThat(statuses.get(5))
      .as("the sixth attempt exceeds capacity - before the fix every attempt here was 401")
      .isEqualTo(429);
  }

  @Test
  @DisplayName("Should still rate limit login when there is no context path")
  void login_withoutAContextPath_isStillRateLimited() throws Exception {
    assertThat(sixFailedLogins("", "10.20.30.2").get(5)).isEqualTo(429);
  }

  private List<Integer> sixFailedLogins(String contextPath, String ip) throws Exception {
    String body = "{\"email\":\"ctx-" + UUID.randomUUID() + "@test.com\",\"password\":\"wrong\"}";
    List<Integer> statuses = new ArrayList<>();
    for (int attempt = 0; attempt < 6; attempt++) {
      statuses.add(mockMvc.perform(post(contextPath + "/auth/login")
          .contextPath(contextPath)
          .with(request -> {
            request.setRemoteAddr(ip);
            return request;
          })
          .contentType(MediaType.APPLICATION_JSON)
          .content(body))
        .andReturn().getResponse().getStatus());
    }
    return statuses;
  }
}
```

And the helper's unit test, `src/test/java/com/enterprise/ordersuite/security/web/RequestPathsTest.java`:

```java
package com.enterprise.ordersuite.security.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathsTest {

  @Test
  void withinApplication_stripsTheContextPath() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
    request.setContextPath("/api");

    assertThat(RequestPaths.withinApplication(request)).isEqualTo("/auth/login");
  }

  @Test
  void withinApplication_withoutAContextPath_returnsTheUri() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");

    assertThat(RequestPaths.withinApplication(request)).isEqualTo("/auth/login");
  }
}
```

- [ ] **Step 3: Run to verify the IT fails the way the finding says**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.ratelimit.ContextPathRateLimitIT"`
Expected: `login_underTheApiContextPath_isRateLimited` FAILS (the sixth status is 401, not 429); `login_withoutAContextPath_isStillRateLimited` PASSES. Keep this output — it is the proof the test is wired to the defect. (`RequestPathsTest` does not compile yet; that is expected.)

- [ ] **Step 4: Implement the helper**

`src/main/java/com/enterprise/ordersuite/security/web/RequestPaths.java`:

```java
package com.enterprise.ordersuite.security.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request path without the context path. getRequestURI() includes the context path
     * (SERVER_CONTEXT_PATH, /api in every real deployment) while MockMvc sends none, so a
     * filter matching the raw URI passes every test and switches off in production.
     * The URI is not normalized; StrictHttpFirewall has already rejected ';', '//', encoded
     * slashes and dot segments before any filter in the security chain runs.
     */
    public static String withinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
```

- [ ] **Step 5: Use it in `AuthRateLimitFilter`**

In `shouldNotFilter`, replace `String path = request.getRequestURI();` with:

```java
    String path = RequestPaths.withinApplication(request);
```

In `doFilterInternal`, replace `String path = wrappedRequest.getRequestURI();` with:

```java
    String path = RequestPaths.withinApplication(wrappedRequest);
```

(`RequestPaths` is in the same package; no import.) Confirm no raw match remains:

Run: `grep -rn "getRequestURI" src/main/java`
Expected: only `RequestPaths.java`.

- [ ] **Step 6: Run the rate-limit suite**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.*"`
Expected: PASS — `ContextPathRateLimitIT` (2), `RequestPathsTest` (2), and every existing `*RateLimitIT` unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/security/web/RequestPaths.java src/main/java/com/enterprise/ordersuite/security/web/AuthRateLimitFilter.java src/test/java/com/enterprise/ordersuite/security/web/RequestPathsTest.java src/test/java/com/enterprise/ordersuite/security/ratelimit/ContextPathRateLimitIT.java
git commit -m "fix(security): match rate-limited auth paths under the servlet context path

getRequestURI() includes the context path, so under SERVER_CONTEXT_PATH=/api the
filter compared /api/auth/login with /auth/login and skipped every request. The
rate-limit ITs passed because MockMvc sends no context path.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Client-IP trust is explicit and off by default (D23)

**Files:**
- Modify: `src/main/resources/application.yml` (`server:` block)
- Modify: the env template `.env.example` (append)
- Modify: `src/main/java/com/enterprise/ordersuite/security/web/AuthRateLimitFilter.java:116` (comment only)

**Interfaces:** none. Configuration only.

Background: once Task 1 lands, rate limiting is live for the first time, keyed on `getRemoteAddr()`. The filter's comment claims Tomcat parses `X-Forwarded-For`, but nothing enables that. The decision (spec D23): bind `server.forward-headers-strategy` from the environment, default `none`.

- [ ] **Step 1: Bind the setting**

In `application.yml`, inside `server:` after the `servlet:` block:

```yaml
  # D23. Whether X-Forwarded-For / X-Forwarded-Proto are trusted. "none": the client IP is the
  # socket address - right locally and when exposed directly, and the only safe default (a
  # client could otherwise forge its IP and dodge every rate limit). Behind a reverse proxy or
  # load balancer set "native": Tomcat's RemoteIpValve then trusts the headers only from
  # server.tomcat.remoteip.internal-proxies (private ranges by default).
  forward-headers-strategy: ${SERVER_FORWARD_HEADERS_STRATEGY:none}
```

- [ ] **Step 2: Document it in the env template**

Append to `.env.example`:

```
# Client-IP trust for rate limiting (D23). Leave at none unless a reverse proxy or load
# balancer sits in front; then set native, and add the proxy to
# server.tomcat.remoteip.internal-proxies if its address is not a private range.
SERVER_FORWARD_HEADERS_STRATEGY=none
```

- [ ] **Step 3: Correct the misleading comment**

In `AuthRateLimitFilter`, replace the line
`// 4. Relying on remote address (delegating X-Forwarded-For parsing security to Tomcat RemoteIpFilter)`
with:

```java
    // 4. The client IP. With server.forward-headers-strategy=native (behind a trusted proxy),
    // Tomcat's RemoteIpValve has already replaced it with the X-Forwarded-For client;
    // with the default none it is the socket address and forwarded headers are ignored (D23).
```

- [ ] **Step 4: Verify the property binds and nothing regressed**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.*"`
Expected: PASS. (An invalid enum value would fail context startup, so a green run proves the binding.) There is no automated test of the header behaviour: MockMvc does not run Tomcat valves, so such a test would pass regardless. The manual check is in Task 11.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/application.yml .env.example src/main/java/com/enterprise/ordersuite/security/web/AuthRateLimitFilter.java
git status --short   # the real env file must NOT appear
git commit -m "fix(security): make client-IP trust for rate limiting explicit, off by default

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Token families in the schema (V21)

**Files:**
- Create: `src/main/resources/db/migration/V21__Refresh_Token_Families.sql`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/domain/RefreshToken.java`
- Modify: `src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java` (the `saveToken` helper)
- Test: `src/test/java/com/enterprise/ordersuite/migration/V21RefreshTokenFamiliesIT.java`

**Interfaces:**
- Produces: `RefreshToken.getFamilyId()` / `setFamilyId(UUID)`; column `refresh_tokens.family_id UUID NOT NULL`, index `idx_refresh_tokens_family_id`.

- [ ] **Step 1: Invoke the `flyway-migrations` skill.** Confirm V20 is the latest migration (`ls src/main/resources/db/migration`).

- [ ] **Step 2: Write the failing migration test**

`src/test/java/com/enterprise/ordersuite/migration/V21RefreshTokenFamiliesIT.java`:

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

import static org.assertj.core.api.Assertions.assertThat;

// Not @IntegrationTest: this has to stop Flyway at V20, plant tokens the way pre-V21 code
// wrote them, and only then apply V21. Its own container, no Spring context.
class V21RefreshTokenFamiliesIT {

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
  void v21_givesEveryExistingTokenItsOwnFamily() throws Exception {
    migrateTo("20");

    try (Connection connection = connect(); Statement statement = connection.createStatement()) {
      statement.execute("""
          insert into users (first_name, last_name, email, password, role_id, created_at, updated_at)
          values ('V21', 'Probe', 'v21-probe@test.com', 'x',
                  (select id from roles where name = 'USER'), now(), now())
          """);
      statement.execute("""
          insert into refresh_tokens (user_id, token_hash, created_at, updated_at, expires_at)
          values ((select id from users where email = 'v21-probe@test.com'), repeat('a', 64),
                  now(), now(), now() + interval '14 days'),
                 ((select id from users where email = 'v21-probe@test.com'), repeat('b', 64),
                  now(), now(), now() + interval '14 days')
          """);
    }

    migrateTo("21");

    assertThat(longOf("select count(*) from refresh_tokens where family_id is null"))
      .as("every pre-existing token must receive a family, or NOT NULL could not hold")
      .isZero();
    assertThat(longOf("select count(distinct family_id) from refresh_tokens"))
      .as("two tokens issued before families existed are unrelated, so they must not share one - "
        + "otherwise reuse of one would revoke the other")
      .isEqualTo(2L);
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

  private static long longOf(String sql) throws Exception {
    try (Connection connection = connect();
         Statement statement = connection.createStatement();
         ResultSet resultSet = statement.executeQuery(sql)) {
      assertThat(resultSet.next()).as("query returned no row: " + sql).isTrue();
      return resultSet.getLong(1);
    }
  }
}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.migration.V21RefreshTokenFamiliesIT"`
Expected: FAIL — Flyway reports no migration for target `21` (or `family_id` does not exist).

- [ ] **Step 4: Write the migration**

`src/main/resources/db/migration/V21__Refresh_Token_Families.sql`:

```sql
-- D16: a family is every token descended from one login. Reuse of a rotated token revokes
-- the whole family. Tokens issued before this migration have no known lineage, so each
-- becomes a family of one and keeps working.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;

UPDATE refresh_tokens SET family_id = gen_random_uuid();

ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
```

- [ ] **Step 5: Map the column on the entity**

In `RefreshToken.java`, add `import java.util.UUID;` and, after `tokenHash`:

```java
    @Column(name = "family_id", nullable = false)
    private UUID familyId;
```

- [ ] **Step 6: Keep the persistence IT's helper valid under NOT NULL**

In `RefreshTokenPersistenceIT.saveToken`, before `return refreshTokenRepository.save(token);`:

```java
    token.setFamilyId(UUID.randomUUID());
```

- [ ] **Step 7: Run the migration test and the persistence IT**

Run: `./gradlew test --tests "com.enterprise.ordersuite.migration.V21RefreshTokenFamiliesIT" --tests "com.enterprise.ordersuite.auth.persistence.RefreshTokenPersistenceIT"`
Expected: PASS. (`RefreshTokenService.issueFor` does not set the family yet — Task 4 does; ITs that log in would fail on NOT NULL until then, so do not run the full suite between Tasks 3 and 4.)

- [ ] **Step 8: Commit**

```bash
git add src/main/resources/db/migration/V21__Refresh_Token_Families.sql src/main/java/com/enterprise/ordersuite/auth/domain/RefreshToken.java src/test/java/com/enterprise/ordersuite/migration/V21RefreshTokenFamiliesIT.java src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java
git commit -m "feat(auth): add refresh token families (V21)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Family-aware `RefreshTokenService`

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenRepository.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/RefreshTokenService.java`
- Test: `src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenServiceTest.java`

**Interfaces:**
- Consumes: `RefreshToken.familyId` (Task 3).
- Produces (all on `RefreshTokenService`):
  - `public static final Duration REFRESH_TTL` (14 days)
  - `IssuedRefreshToken issueFor(User user)` — new family
  - `IssuedRefreshToken rotate(RefreshToken current)` — marks `current` used, issues successor in `current`'s family
  - `RefreshToken findForRotationOrNull(String rawRefreshToken)` — row-locked lookup, any state; `null` for null/blank/unknown
  - `boolean isExpired(RefreshToken token)`
  - `void revokeFamily(RefreshToken token)`
  - `void revokeAllFor(User user)`
  - existing `hash(String)`, `findByHashOrNull(String)` unchanged
- Produces on `RefreshTokenRepository`: `findByTokenHashForUpdate(String)`, `revokeFamily(UUID, Instant)`, `revokeAllForUser(Long, Instant)`.

The old `getActiveTokenOrNull`, `markUsed`, `revoke` stay in this task (AuthenticationService still calls them) and are removed in Task 5.

- [ ] **Step 1: Write the failing unit tests**

Replace `RefreshTokenServiceTest.java` with (keeps its existing 4-space style and fixed clock):

```java
package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.domain.RefreshToken;
import com.enterprise.ordersuite.auth.persistence.RefreshTokenRepository;
import com.enterprise.ordersuite.auth.service.tokens.RefreshTokenGenerator;
import com.enterprise.ordersuite.identity.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-28T12:00:00Z");

    private RefreshTokenRepository repo;
    private RefreshTokenGenerator generator;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        repo = mock(RefreshTokenRepository.class);
        generator = mock(RefreshTokenGenerator.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new RefreshTokenService(repo, generator, clock);
        when(repo.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void issueFor_createsTokenAndReturnsRawToken() {
        User user = new User();
        when(generator.generate()).thenReturn("raw-refresh-token");

        var issued = service.issueFor(user);

        assertThat(issued.rawToken()).isEqualTo("raw-refresh-token");
        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(RefreshTokenService.REFRESH_TTL));
        verify(repo).save(argThat(t ->
                t.getUser() == user
                        && t.getTokenHash() != null
                        && t.getTokenHash().length() == 64
                        && t.getFamilyId() != null));
    }

    @Test
    void issueFor_startsANewFamilyOnEveryCall() {
        when(generator.generate()).thenReturn("a", "b");

        service.issueFor(new User());
        service.issueFor(new User());

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo, times(2)).save(saved.capture());
        List<RefreshToken> tokens = saved.getAllValues();
        assertThat(tokens.get(0).getFamilyId())
                .as("two logins are two sessions; sharing a family would let reuse in one kill the other")
                .isNotEqualTo(tokens.get(1).getFamilyId());
    }

    @Test
    void rotate_marksCurrentUsed_andIssuesSuccessorInTheSameFamily() {
        User user = new User();
        UUID family = UUID.randomUUID();
        RefreshToken current = new RefreshToken();
        current.setUser(user);
        current.setFamilyId(family);
        when(generator.generate()).thenReturn("successor");

        var issued = service.rotate(current);

        assertThat(issued.rawToken()).isEqualTo("successor");
        assertThat(current.getUsedAt()).isEqualTo(NOW);
        verify(repo).save(current);
        verify(repo).save(argThat(t -> t != current && t.getUser() == user && family.equals(t.getFamilyId())));
    }

    @Test
    void findForRotationOrNull_blankInput_returnsNullWithoutQuerying() {
        assertThat(service.findForRotationOrNull(null)).isNull();
        assertThat(service.findForRotationOrNull("  ")).isNull();
        verifyNoInteractions(repo);
    }

    @Test
    void findForRotationOrNull_unknownToken_returnsNull() {
        when(repo.findByTokenHashForUpdate(anyString())).thenReturn(java.util.Optional.empty());

        assertThat(service.findForRotationOrNull("anything")).isNull();
    }

    @Test
    void isExpired_comparesAgainstTheClock() {
        RefreshToken live = new RefreshToken();
        live.setExpiresAt(NOW.plus(Duration.ofSeconds(1)));
        RefreshToken dead = new RefreshToken();
        dead.setExpiresAt(NOW.minus(Duration.ofSeconds(1)));

        assertThat(service.isExpired(live)).isFalse();
        assertThat(service.isExpired(dead)).isTrue();
    }

    @Test
    void revokeFamily_revokesEveryTokenOfThatFamilyAtNow() {
        RefreshToken token = new RefreshToken();
        UUID family = UUID.randomUUID();
        token.setFamilyId(family);

        service.revokeFamily(token);

        verify(repo).revokeFamily(family, NOW);
    }

    @Test
    void revokeAllFor_revokesEveryTokenOfThatUserAtNow() {
        User user = new User();
        user.setId(42L);

        service.revokeAllFor(user);

        verify(repo).revokeAllForUser(42L, NOW);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.service.RefreshTokenServiceTest"`
Expected: FAIL — compilation errors (`REFRESH_TTL`, `rotate`, `findForRotationOrNull`, `findByTokenHashForUpdate`, … not defined).

- [ ] **Step 3: Add the repository methods**

In `RefreshTokenRepository.java` add imports `jakarta.persistence.LockModeType`, `org.springframework.data.jpa.repository.Lock`, `java.util.UUID`, and these methods (existing ones unchanged; parameter names bind without `@Param`, as `deleteExpired` already does):

```java
    // Serializes concurrent presentations of one token: the second waits, then sees used_at.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rt from RefreshToken rt where rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying
    @Transactional
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.familyId = :familyId and rt.revokedAt is null
            """)
    int revokeFamily(UUID familyId, Instant now);

    @Modifying
    @Transactional
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :now
            where rt.user.id = :userId and rt.revokedAt is null
            """)
    int revokeAllForUser(Long userId, Instant now);
```

- [ ] **Step 4: Implement the service methods**

In `RefreshTokenService.java`: add `import java.util.UUID;`, replace the `refreshTtl` field with the constant, route `issueFor` through a family-taking helper, and add the new methods:

```java
    public static final Duration REFRESH_TTL = Duration.ofDays(14);

    public IssuedRefreshToken issueFor(User user) {
        return issue(user, UUID.randomUUID());
    }

    public IssuedRefreshToken rotate(RefreshToken current) {
        current.setUsedAt(Instant.now(clock));
        refreshTokenRepository.save(current);
        return issue(current.getUser(), current.getFamilyId());
    }

    // Returns the token in any state - used, revoked or expired - so the caller can tell
    // reuse apart from an unknown token. Must run inside a transaction (row lock).
    public RefreshToken findForRotationOrNull(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        return refreshTokenRepository.findByTokenHashForUpdate(TokenHashing.sha256Hex(rawRefreshToken))
                .orElse(null);
    }

    public boolean isExpired(RefreshToken token) {
        return token.isExpired(Instant.now(clock));
    }

    public void revokeFamily(RefreshToken token) {
        refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now(clock));
    }

    public void revokeAllFor(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now(clock));
    }

    private IssuedRefreshToken issue(User user, UUID familyId) {
        String raw = refreshTokenGenerator.generate();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHashing.sha256Hex(raw));
        token.setFamilyId(familyId);
        // BaseEntity handles createdAt
        token.setExpiresAt(Instant.now(clock).plus(REFRESH_TTL));

        refreshTokenRepository.save(token);

        return new IssuedRefreshToken(raw, token.getExpiresAt());
    }
```

(Delete the old body of `issueFor` and the `private final Duration refreshTtl` field; `getActiveTokenOrNull`, `markUsed`, `revoke`, `hash`, `findByHashOrNull` stay for now.)

- [ ] **Step 5: Run the unit tests**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.service.RefreshTokenServiceTest"`
Expected: PASS (8 tests).

- [ ] **Step 6: Run the auth ITs to confirm login works again with families**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenRepository.java src/main/java/com/enterprise/ordersuite/auth/service/RefreshTokenService.java src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenServiceTest.java
git commit -m "feat(auth): issue, rotate and revoke refresh tokens by family

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Rotation with reuse detection, and 401 for an invalid refresh (D16, D17, D20)

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/AuthenticationService.java:86-118`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/RefreshTokenService.java` (remove dead methods)
- Modify: `src/main/java/com/enterprise/ordersuite/auth/controllers/AuthenticationController.java` (refresh/logout call sites only)
- Modify: `src/main/java/com/enterprise/ordersuite/api/errors/AuthExceptionHandler.java:81-84`
- Modify: `src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java` (the `getActiveTokenOrNull` test)
- Replace: `src/test/java/com/enterprise/ordersuite/auth/controllers/RefreshTokenFlowIT.java`
- Test: `src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenConcurrencyIT.java`

**Interfaces:**
- Consumes: Task 4's `RefreshTokenService` API.
- Produces: `AuthenticationService.refresh(String rawRefreshToken) : AuthResponse` and `AuthenticationService.logout(String rawRefreshToken) : void` (the `RefreshRequest`/`LogoutRequest` overloads are gone). A null/blank/unknown/expired/used/revoked token throws `InvalidRefreshTokenException`, mapped to 401.

- [ ] **Step 1: Invoke the `spring-security-changes` skill.** The questions it requires were answered on 2026-09-25 and are recorded in the spec (D16–D21); do not re-ask.

- [ ] **Step 2: Rewrite `RefreshTokenFlowIT` to describe the target behavior (failing)**

Replace the file with:

```java
package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.LogoutRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Body-borne refresh: the live (pre-Phase 6) path, kept working for backward compatibility.
@IntegrationTest
@AutoConfigureMockMvc
class RefreshTokenFlowIT {

  private static final String RAW_PASSWORD = "Password123!";
  private static final String TEST_IP = "10.10.10.10";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  private String testEmail;

  @BeforeEach
  void setUp() throws Exception {
    testEmail = "testuser_" + UUID.randomUUID() + "@example.com";

    RegisterRequest registerRequest = new RegisterRequest();
    registerRequest.setFirstName("Test");
    registerRequest.setLastName("User");
    registerRequest.setEmail(testEmail);
    registerRequest.setPassword(RAW_PASSWORD);

    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(registerRequest)))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_rotatesOnEveryCall_andLogoutRevokes() throws Exception {
    String first = login();

    String second = refreshToken(refresh(first).andExpect(status().isOk()));
    assertThat(second).isNotBlank().isNotEqualTo(first);

    String third = refreshToken(refresh(second).andExpect(status().isOk()));
    assertThat(third).isNotBlank().isNotEqualTo(second).isNotEqualTo(first);

    logout(third).andExpect(status().isOk());

    refresh(third)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    logout(third).andExpect(status().isOk());
  }

  @Test
  void refresh_reuseOfARotatedToken_revokesTheWholeFamily() throws Exception {
    String first = login();
    String second = refreshToken(refresh(first).andExpect(status().isOk()));

    refresh(first)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

    // The guard against a rolled-back revocation: the successor must be dead too.
    refresh(second)
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void refresh_reuseInOneFamily_leavesAnotherLoginAlive() throws Exception {
    String deviceA = login();
    String deviceB = login();

    refresh(deviceA).andExpect(status().isOk());
    refresh(deviceA).andExpect(status().isUnauthorized());

    refresh(deviceB).andExpect(status().isOk());
  }

  @Test
  void refresh_unknownToken_returns401() throws Exception {
    refresh("not-a-real-token")
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  private String login() throws Exception {
    String body = mockMvc.perform(post("/auth/login")
        .with(request -> {
          request.setRemoteAddr(TEST_IP);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(testEmail, RAW_PASSWORD))))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("refreshToken").asText();
  }

  private ResultActions refresh(String refreshToken) throws Exception {
    return mockMvc.perform(post("/auth/refresh")
      .with(request -> {
        request.setRemoteAddr(TEST_IP);
        return request;
      })
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))));
  }

  private ResultActions logout(String refreshToken) throws Exception {
    return mockMvc.perform(post("/auth/logout")
      .with(request -> {
        request.setRemoteAddr(TEST_IP);
        return request;
      })
      .contentType(MediaType.APPLICATION_JSON)
      .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))));
  }

  private String refreshToken(ResultActions result) throws Exception {
    return objectMapper.readTree(result.andReturn().getResponse().getContentAsString())
      .get("refreshToken").asText();
  }
}
```

- [ ] **Step 3: Write the concurrency IT (failing)**

`src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenConcurrencyIT.java`:

```java
package com.enterprise.ordersuite.auth.service;

import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.auth.service.exceptions.InvalidRefreshTokenException;
import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class RefreshTokenConcurrencyIT {

  @Autowired
  private AuthenticationService authenticationService;

  @Test
  void refresh_sameTokenPresentedConcurrently_succeedsExactlyOnce() throws Exception {
    RegisterRequest register = new RegisterRequest();
    register.setFirstName("Race");
    register.setLastName("Condition");
    register.setEmail("race-" + UUID.randomUUID() + "@test.com");
    register.setPassword("Password123!");
    String token = authenticationService.register(register).getRefreshToken();

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<Boolean> attempt = () -> {
        start.await();
        try {
          authenticationService.refresh(token);
          return true;
        } catch (InvalidRefreshTokenException e) {
          return false;
        }
      };
      Future<Boolean> first = pool.submit(attempt);
      Future<Boolean> second = pool.submit(attempt);
      start.countDown();

      List<Boolean> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

      assertThat(outcomes)
        .as("without the row lock both calls pass the used_at check and one token yields two sessions")
        .containsExactlyInAnyOrder(true, false);
    } finally {
      pool.shutdownNow();
    }
  }
}
```

- [ ] **Step 4: Run both to verify they fail**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.controllers.RefreshTokenFlowIT" --tests "com.enterprise.ordersuite.auth.service.RefreshTokenConcurrencyIT"`
Expected: FAIL — `RefreshTokenConcurrencyIT` does not compile (`refresh(String)` does not exist). Temporarily comment it out and re-run to see `RefreshTokenFlowIT` fail on status 400 vs 401 and on `refresh(second)` returning 200 after reuse. Keep that output; restore the file.

- [ ] **Step 5: Implement the flow in `AuthenticationService`**

Remove the imports of `RefreshRequest` and `LogoutRequest`. Replace `refresh(...)` and `logout(...)` with:

```java
  // noRollbackFor: the reuse branch revokes the family and then answers 401. If the
  // exception rolled that back, reuse detection would silently do nothing.
  @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
  public AuthResponse refresh(String rawRefreshToken) {
    RefreshToken existing = refreshTokenService.findForRotationOrNull(rawRefreshToken);
    if (existing == null) {
      throw new InvalidRefreshTokenException();
    }

    // D16/D17: a used or revoked token presented again is reuse - kill the whole family.
    if (existing.isUsed() || existing.isRevoked()) {
      refreshTokenService.revokeFamily(existing);
      throw new InvalidRefreshTokenException();
    }

    if (refreshTokenService.isExpired(existing)) {
      throw new InvalidRefreshTokenException();
    }

    User user = existing.getUser();

    if (!Boolean.TRUE.equals(user.getActive())) {
      refreshTokenService.revokeFamily(existing);
      throw new InvalidRefreshTokenException();
    }

    var rotated = refreshTokenService.rotate(existing);

    return new AuthResponse(jwtService.generateToken(user), rotated.rawToken());
  }

  @Transactional
  public void logout(String rawRefreshToken) {
    RefreshToken token = refreshTokenService.findByHashOrNull(refreshTokenService.hash(rawRefreshToken));

    if (token == null) {
      return;
    }

    refreshTokenService.revokeFamily(token);
  }
```

- [ ] **Step 6: Point the controller at the new signatures**

In `AuthenticationController`, change only the two call sites (the cookie work is Task 9):

```java
        AuthResponse response = authenticationService.refresh(request.refreshToken());
```
```java
        authenticationService.logout(request.refreshToken());
```

- [ ] **Step 7: Map the exception to 401 (D20)**

In `AuthExceptionHandler.handleInvalidRefreshToken`, change `HttpStatus.BAD_REQUEST` to `HttpStatus.UNAUTHORIZED`. Then:

Run: `grep -rn "INVALID_REFRESH_TOKEN" src/test`
Expected: only `RefreshTokenFlowIT` (already asserting 401). Any other hit asserting 400 → change it to `isUnauthorized()`.

- [ ] **Step 8: Remove the now-dead service methods and move their one test**

Delete `getActiveTokenOrNull`, `markUsed` and `revoke` from `RefreshTokenService`, then confirm nothing calls them:

Run: `grep -rn "getActiveTokenOrNull\|markUsed\|refreshTokenService.revoke(" src`
Expected: only `RefreshTokenPersistenceIT`. Replace its test with the same D15 guard expressed through `isExpired`:

```java
  // The reason D15 is in Phase 0: expiry was compared in an unstated zone. Around the
  // boundary, a 3-hour disagreement between writer and reader decides the answer.
  @Test
  void isExpired_honoursExpiryWithinMinutes_notHours() {
    Instant now = clock.instant();
    String expiringSoon = "raw-" + UUID.randomUUID();
    String justExpired = "raw-" + UUID.randomUUID();
    saveToken(expiringSoon, now.plus(Duration.ofMinutes(5)));
    saveToken(justExpired, now.minus(Duration.ofMinutes(5)));

    assertThat(refreshTokenService.isExpired(reload(expiringSoon)))
      .as("a token with five minutes left is live")
      .isFalse();
    assertThat(refreshTokenService.isExpired(reload(justExpired)))
      .as("a token that expired five minutes ago is not")
      .isTrue();
  }

  private RefreshToken reload(String rawToken) {
    return refreshTokenRepository.findByTokenHash(TokenHashing.sha256Hex(rawToken)).orElseThrow();
  }
```

- [ ] **Step 9: Run the affected tests**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.*" --tests "com.enterprise.ordersuite.security.ratelimit.*"`
Expected: PASS, including `RefreshTokenConcurrencyIT` and all four `RefreshTokenFlowIT` tests. (The rate-limit ITs assert "not 429" / "200" only, so reuse answering 401 does not affect them.)

- [ ] **Step 10: Commit**

```bash
git add -A src/main/java/com/enterprise/ordersuite/auth src/main/java/com/enterprise/ordersuite/api/errors/AuthExceptionHandler.java src/test/java/com/enterprise/ordersuite/auth
git status --short   # only the files listed in this task
git commit -m "feat(auth): revoke the token family on refresh reuse; invalid refresh is 401

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Keep used and revoked tokens until their own expiry (D24)

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/RefreshTokenCleanupService.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenRepository.java` (remove `deleteUsedOrRevokedBefore`)
- Modify: `src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenCleanupServiceTest.java`
- Modify: `src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java`

**Interfaces:**
- Consumes: Task 5's reuse detection (it checks used/revoked before expired).
- Produces: `RefreshTokenCleanupService.cleanupNow() : CleanupResult` with `record CleanupResult(int expiredDeleted)` — `usedRevokedDeleted` is gone. `RefreshTokenCleanupScheduler` ignores the result, so it needs no change.

Background: cleanup deletes used/revoked tokens 7 days after use; a replay after that is "unknown", answers a plain 401 and revokes nothing. Reuse must be detectable for a token's whole 14-day life.

- [ ] **Step 1: Write the failing tests**

Replace the body of `RefreshTokenCleanupServiceTest` (keep its imports, `@ExtendWith`, `@Mock` and `setUp`) with:

```java
  @Test
  void cleanupNow_deletesOnlyExpiredTokens() {
    when(repo.deleteExpired(any())).thenReturn(3);

    var result = service.cleanupNow();

    assertThat(result.expiredDeleted()).isEqualTo(3);
    verify(repo).deleteExpired(Instant.parse("2026-01-29T12:00:00Z"));
    verifyNoMoreInteractions(repo);
  }

  @Test
  void cleanupNow_whenNothingExpired_returnsZero() {
    when(repo.deleteExpired(any())).thenReturn(0);

    assertThat(service.cleanupNow().expiredDeleted()).isZero();
  }
```

In `RefreshTokenPersistenceIT`, add `@Autowired private RefreshTokenCleanupService refreshTokenCleanupService;` (import `com.enterprise.ordersuite.auth.service.RefreshTokenCleanupService`) and:

```java
  @Test
  void cleanupNow_keepsAUsedTokenUntilItsOwnExpiry() {
    RefreshToken token = saveToken("raw-" + UUID.randomUUID(), clock.instant().plus(Duration.ofDays(13)));
    token.setUsedAt(clock.instant().minus(Duration.ofDays(8)));
    refreshTokenRepository.save(token);

    refreshTokenCleanupService.cleanupNow();

    assertThat(refreshTokenRepository.findById(token.getId()))
      .as("a replay of this token must still be recognised as reuse, so it must still exist")
      .isPresent();
  }
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.service.RefreshTokenCleanupServiceTest" --tests "com.enterprise.ordersuite.auth.persistence.RefreshTokenPersistenceIT"`
Expected: FAIL — the unit test sees the extra `deleteUsedOrRevokedBefore` interaction; the IT finds the token deleted (used 8 days ago, past the 7-day cutoff).

- [ ] **Step 3: Implement**

`RefreshTokenCleanupService` — remove the `usedRevokedRetention` field and replace `cleanupNow` and the record:

```java
    // Used and revoked tokens stay until their own expiry: a replayed token must still be
    // found to be recognised as reuse (D24). deleteExpired removes every token past expiry.
    public CleanupResult cleanupNow() {
        return new CleanupResult(refreshTokenRepository.deleteExpired(Instant.now(clock)));
    }

    public record CleanupResult(int expiredDeleted) {}
```

Remove the now-unused `java.time.Duration` import. In `RefreshTokenRepository`, delete `deleteUsedOrRevokedBefore` and its annotations. Confirm:

Run: `grep -rn "deleteUsedOrRevokedBefore\|usedRevokedDeleted" src`
Expected: no output.

- [ ] **Step 4: Run to verify they pass**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/auth/service/RefreshTokenCleanupService.java src/main/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenRepository.java src/test/java/com/enterprise/ordersuite/auth/service/RefreshTokenCleanupServiceTest.java src/test/java/com/enterprise/ordersuite/auth/persistence/RefreshTokenPersistenceIT.java
git commit -m "fix(auth): keep used refresh tokens until expiry so reuse stays detectable

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Password reset revokes every refresh token of the user (D21)

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/auth/service/PasswordResetService.java` (constructor; `resetPassword` after `userRepository.save(user)`)
- Modify: `src/test/java/com/enterprise/ordersuite/auth/service/PasswordResetServiceTest.java`
- Modify: `src/test/java/com/enterprise/ordersuite/auth/controllers/AuthenticationControllerIT.java`

**Interfaces:**
- Consumes: `RefreshTokenService.revokeAllFor(User)` (Task 4).
- Produces: `PasswordResetService` constructor gains a final parameter `RefreshTokenService refreshTokenService` (after `linkBuilder`).

- [ ] **Step 1: Write the failing unit assertions**

In `PasswordResetServiceTest`: add field `private RefreshTokenService refreshTokenService;`, in `setUp` add `refreshTokenService = mock(RefreshTokenService.class);` and pass it as the last constructor argument. At the end of `resetPassword_whenValidToken_updatesPassword_archivesOldHash_andPrunesOldEntries` add:

```java
    verify(refreshTokenService).revokeAllFor(user);
```

At the end of `resetPassword_whenUserInactive_throwsGenericInvalidToken_andDoesNotPersist` and `resetPassword_whenNewPasswordMatchesCurrentActivePassword_throwsPasswordReuseException` add:

```java
    verifyNoInteractions(refreshTokenService);
```

- [ ] **Step 2: Write the failing IT**

In `AuthenticationControllerIT`, add imports `com.enterprise.ordersuite.auth.dtos.AuthRequest`, `com.enterprise.ordersuite.auth.dtos.RefreshRequest`, and this test plus helpers:

```java
  @Test
  void resetPassword_revokesEveryRefreshTokenOfThatUser_andNoOneElses() throws Exception {
    String victim = saveActiveUser("OldPass123!");
    String bystander = saveActiveUser("OldPass123!");
    String victimPhone = loginForRefreshToken(victim, "OldPass123!");
    String victimLaptop = loginForRefreshToken(victim, "OldPass123!");
    String bystanderToken = loginForRefreshToken(bystander, "OldPass123!");

    String rawToken = passwordResetService.requestPasswordReset(victim).orElseThrow();
    ResetPasswordRequest request = new ResetPasswordRequest();
    request.setToken(rawToken);
    request.setNewPassword("NewEnterprisePass!@#");
    mockMvc.perform(post("/auth/reset-password")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());

    assertThat(refreshStatus(victimPhone)).as("a reset must end every existing session").isEqualTo(401);
    assertThat(refreshStatus(victimLaptop)).as("a reset must end every existing session").isEqualTo(401);
    assertThat(refreshStatus(bystanderToken)).as("another user's session is untouched").isEqualTo(200);
  }

  private String saveActiveUser(String rawPassword) {
    String email = "reset-" + UUID.randomUUID() + "@test.com";
    User user = new User();
    user.setEmail(email);
    user.setPassword(passwordEncoder.encode(rawPassword));
    user.setRole(userRole);
    user.setActive(true);
    user.setFirstName("Reset");
    user.setLastName("User");
    userRepository.save(user);
    return email;
  }

  private String loginForRefreshToken(String email, String rawPassword) throws Exception {
    String body = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, rawPassword))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("refreshToken").asText();
  }

  private int refreshStatus(String refreshToken) throws Exception {
    return mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(refreshToken))))
      .andReturn().getResponse().getStatus();
  }
```

- [ ] **Step 3: Run to verify both fail**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.service.PasswordResetServiceTest" --tests "com.enterprise.ordersuite.auth.controllers.AuthenticationControllerIT"`
Expected: FAIL — unit test does not compile (constructor arity); once the constructor exists, `revokeAllFor` is never invoked and the IT sees 200 for the victim.

- [ ] **Step 4: Implement**

In `PasswordResetService`: add field `private final RefreshTokenService refreshTokenService;`, add the constructor parameter `RefreshTokenService refreshTokenService` after `linkBuilder` and assign it. In `resetPassword`, directly after `userRepository.save(user);`:

```java
    // D21: a reset says the credentials may be compromised - end every existing session.
    refreshTokenService.revokeAllFor(user);
```

(`RefreshTokenService` is in the same package; no import needed.)

- [ ] **Step 5: Run to verify they pass**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.service.PasswordResetServiceTest" --tests "com.enterprise.ordersuite.auth.controllers.AuthenticationControllerIT"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/auth/service/PasswordResetService.java src/test/java/com/enterprise/ordersuite/auth/service/PasswordResetServiceTest.java src/test/java/com/enterprise/ordersuite/auth/controllers/AuthenticationControllerIT.java
git commit -m "fix(auth): revoke every refresh token when a password is reset

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Identity claims in the access token

**Files:**
- Modify: `src/main/java/com/enterprise/ordersuite/security/jwt/JwtService.java:27-32`
- Test: `src/test/java/com/enterprise/ordersuite/security/jwt/JwtServiceTest.java`

**Interfaces:**
- Produces: access-token claims `firstName`, `lastName`, `email` (strings) alongside existing `userId`, `roles`, `sub`, `iat`, `exp`.

- [ ] **Step 1: Write the failing test**

```java
package com.enterprise.ordersuite.security.jwt;

import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

  @Test
  void generateToken_carriesDisplayIdentityClaims_andKeepsTheExistingOnes() {
    JwtProperties properties = new JwtProperties();
    properties.setSecret(Base64.getEncoder()
      .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
    properties.setExpiration(60_000);
    JwtService jwtService = new JwtService(properties, Clock.systemUTC());

    Role role = new Role();
    role.setName("ADMIN");
    User user = new User();
    user.setId(7L);
    user.setEmail("ana@test.com");
    user.setFirstName("Ana");
    user.setLastName("Souza");
    user.setRole(role);

    String token = jwtService.generateToken(user);

    assertThat(jwtService.extractClaim(token, c -> c.get("firstName", String.class))).isEqualTo("Ana");
    assertThat(jwtService.extractClaim(token, c -> c.get("lastName", String.class))).isEqualTo("Souza");
    assertThat(jwtService.extractClaim(token, c -> c.get("email", String.class))).isEqualTo("ana@test.com");
    assertThat(jwtService.extractEmail(token)).as("sub is unchanged").isEqualTo("ana@test.com");
    assertThat(jwtService.extractUserId(token)).isEqualTo(7L);
    assertThat(jwtService.extractRoles(token)).isEqualTo(List.of("ADMIN"));
  }
}
```

(`extractEmail`, `extractUserId`, `extractRoles` and `extractClaim` already exist on `JwtService`.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.jwt.JwtServiceTest"`
Expected: FAIL — `firstName` claim is `null`.

- [ ] **Step 3: Implement**

In `JwtService.generateToken`, after the `roles` line:

```java
    // Display data only - a JWT payload is base64, not encrypted. Never phone or address.
    claims.put("firstName", user.getFirstName());
    claims.put("lastName", user.getLastName());
    claims.put("email", user.getEmail());
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.jwt.JwtServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/security/jwt/JwtService.java src/test/java/com/enterprise/ordersuite/security/jwt/JwtServiceTest.java
git commit -m "feat(auth): add firstName, lastName and email claims to the access token

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: The refresh cookie (D12, D18)

**Files:**
- Create: `src/main/java/com/enterprise/ordersuite/security/config/RefreshCookieProperties.java`
- Create: `src/main/java/com/enterprise/ordersuite/auth/controllers/RefreshCookieFactory.java`
- Create: `src/main/java/com/enterprise/ordersuite/auth/controllers/RefreshTokenSource.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/controllers/AuthenticationController.java`
- Modify: `src/main/java/com/enterprise/ordersuite/auth/dtos/RefreshRequest.java`, `LogoutRequest.java`
- Modify: `src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java:44` (`@EnableConfigurationProperties`)
- Modify: `src/main/resources/application.yml` (`security:` block), `.env.example`
- Test: `src/test/java/com/enterprise/ordersuite/auth/controllers/RefreshCookieFactoryTest.java`, `RefreshTokenSourceTest.java`, `RefreshCookieIT.java`

**Interfaces:**
- Consumes: `RefreshTokenService.REFRESH_TTL`, `AuthenticationService.refresh(String)` / `logout(String)` (Task 5).
- Produces:
  - `record RefreshCookieProperties(boolean secure, String sameSite)` with `public static final String COOKIE_NAME = "refreshToken"`
  - `RefreshCookieFactory.issue(String rawToken, String contextPath) : ResponseCookie`, `RefreshCookieFactory.clear(String contextPath) : ResponseCookie`
  - `RefreshTokenSource.resolve(String cookieValue, String bodyValue) : String` (nullable)

- [ ] **Step 1: Invoke `api-contract-sync` and re-read the manifest's `/auth/*` `Set-Cookie` headers** (`docs/contracts/backend-integration-manifest.openapi.yaml`, paths `/auth/login` … `/auth/logout`). The cookie below matches them; nothing is reported back (spec, "Contract work").

- [ ] **Step 2: Write the failing unit tests**

`RefreshTokenSourceTest.java`:

```java
package com.enterprise.ordersuite.auth.controllers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenSourceTest {

  @Test
  void resolve_cookieOnly_returnsCookie() {
    assertThat(RefreshTokenSource.resolve("from-cookie", null)).isEqualTo("from-cookie");
  }

  @Test
  void resolve_bodyOnly_returnsBody() {
    assertThat(RefreshTokenSource.resolve(null, "from-body")).isEqualTo("from-body");
  }

  @Test
  void resolve_both_cookieWins() {
    assertThat(RefreshTokenSource.resolve("from-cookie", "from-body"))
      .as("a stale localStorage value in a half-migrated frontend must not override the fresh cookie")
      .isEqualTo("from-cookie");
  }

  @Test
  void resolve_blankCookie_fallsBackToBody() {
    assertThat(RefreshTokenSource.resolve("", "from-body")).isEqualTo("from-body");
  }

  @Test
  void resolve_neither_returnsNull() {
    assertThat(RefreshTokenSource.resolve(null, null)).isNull();
    assertThat(RefreshTokenSource.resolve(" ", "")).isNull();
  }
}
```

`RefreshCookieFactoryTest.java`:

```java
package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshCookieFactoryTest {

  @Test
  void issue_carriesEveryAttributeTheContractNames() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(true, "Lax"));

    ResponseCookie cookie = factory.issue("raw", "/api");

    assertThat(cookie.getName()).isEqualTo("refreshToken");
    assertThat(cookie.getValue()).isEqualTo("raw");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
    assertThat(cookie.getPath())
      .as("production serves under context path /api; the cookie must reach /api/auth/refresh")
      .isEqualTo("/api/auth");
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(14));
  }

  @Test
  void issue_followsProperties_forLocalHttpDevelopment() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(false, "Strict"));

    ResponseCookie cookie = factory.issue("raw", "");

    assertThat(cookie.isSecure()).isFalse();
    assertThat(cookie.getSameSite()).isEqualTo("Strict");
    assertThat(cookie.getPath()).isEqualTo("/auth");
  }

  @Test
  void clear_mirrorsTheIssuedCookie_withZeroMaxAge() {
    RefreshCookieFactory factory = new RefreshCookieFactory(new RefreshCookieProperties(true, "Lax"));

    ResponseCookie cookie = factory.clear("/api");

    assertThat(cookie.getName()).isEqualTo("refreshToken");
    assertThat(cookie.getValue()).isEmpty();
    assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    assertThat(cookie.getPath()).as("a browser only clears a cookie whose path matches").isEqualTo("/api/auth");
    assertThat(cookie.isHttpOnly()).isTrue();
    assertThat(cookie.isSecure()).isTrue();
    assertThat(cookie.getSameSite()).isEqualTo("Lax");
  }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.controllers.RefreshTokenSourceTest" --tests "com.enterprise.ordersuite.auth.controllers.RefreshCookieFactoryTest"`
Expected: FAIL — classes do not exist.

- [ ] **Step 4: Implement properties, factory and source**

`RefreshCookieProperties.java`:

```java
package com.enterprise.ordersuite.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// D12: production-safe defaults; local HTTP development opts out of Secure explicitly.
@ConfigurationProperties(prefix = "security.refresh-cookie")
public record RefreshCookieProperties(
    @DefaultValue("true") boolean secure,
    @DefaultValue("Lax") String sameSite
) {
    public static final String COOKIE_NAME = "refreshToken";
}
```

`RefreshCookieFactory.java`:

```java
package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.service.RefreshTokenService;
import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

    private final RefreshCookieProperties properties;

    public ResponseCookie issue(String rawToken, String contextPath) {
        return base(rawToken, contextPath).maxAge(RefreshTokenService.REFRESH_TTL).build();
    }

    public ResponseCookie clear(String contextPath) {
        return base("", contextPath).maxAge(Duration.ZERO).build();
    }

    // Path is derived from the request's context path, which is env-bound (SERVER_CONTEXT_PATH).
    private ResponseCookie.ResponseCookieBuilder base(String value, String contextPath) {
        return ResponseCookie.from(RefreshCookieProperties.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(contextPath + "/auth");
    }
}
```

`RefreshTokenSource.java`:

```java
package com.enterprise.ordersuite.auth.controllers;

// D18: the cookie is the target source; the body is the backward-compatible fallback.
final class RefreshTokenSource {

    private RefreshTokenSource() {
    }

    static String resolve(String cookieValue, String bodyValue) {
        if (cookieValue != null && !cookieValue.isBlank()) {
            return cookieValue;
        }
        if (bodyValue != null && !bodyValue.isBlank()) {
            return bodyValue;
        }
        return null;
    }
}
```

In `SecurityConfig`, change the annotation to
`@EnableConfigurationProperties({RateLimitProperties.class, RefreshCookieProperties.class})`.

- [ ] **Step 5: Run the unit tests**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.controllers.RefreshTokenSourceTest" --tests "com.enterprise.ordersuite.auth.controllers.RefreshCookieFactoryTest"`
Expected: PASS.

- [ ] **Step 6: Write the failing cookie IT**

`src/test/java/com/enterprise/ordersuite/auth/controllers/RefreshCookieIT.java`:

```java
package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Pinned: spring-dotenv loads the developer's .env, which sets REFRESH_COOKIE_SECURE=false
// locally. These assertions describe what production ships, on every machine.
// MockMvc has no context path, so the cookie path is /auth here; /api/auth is unit-tested.
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
  "security.refresh-cookie.secure=true",
  "security.refresh-cookie.same-site=Lax",
  "security.cors.allowed-origins=http://localhost:3000"
})
class RefreshCookieIT {

  private static final String PASSWORD = "Password123!";
  private static final String FRONTEND = "http://localhost:3000";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void login_setsTheRefreshCookie_matchingTheBodyToken() throws Exception {
    MvcResult result = login(register());

    String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie)
      .startsWith("refreshToken=")
      .contains("Path=/auth", "Max-Age=1209600", "HttpOnly", "Secure", "SameSite=Lax");
    assertThat(cookieValue(result)).isEqualTo(bodyRefreshToken(result));
  }

  @Test
  void register_setsTheRefreshCookie() throws Exception {
    RegisterRequest request = registerRequest();
    MvcResult result = mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(cookieValue(result)).isNotBlank().isEqualTo(bodyRefreshToken(result));
  }

  @Test
  void refresh_viaCookieWithNoBody_rotatesAndSetsANewCookie() throws Exception {
    String first = cookieValue(login(register()));

    MvcResult result = mockMvc.perform(post("/auth/refresh")
        .cookie(new Cookie("refreshToken", first))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.accessToken").isNotEmpty())
      .andReturn();

    assertThat(cookieValue(result)).isNotBlank().isNotEqualTo(first);
  }

  @Test
  void refresh_cookieAndBodyBothPresent_cookieWins() throws Exception {
    String token = cookieValue(login(register()));

    mockMvc.perform(post("/auth/refresh")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest("stale-local-storage-value"))))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_jsonContentTypeButNoTokenAnywhere_returns401() throws Exception {
    mockMvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isUnauthorized())
      .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
  }

  @Test
  void logout_viaCookie_clearsTheCookie_andRevokesTheFamily() throws Exception {
    String token = cookieValue(login(register()));

    MvcResult result = mockMvc.perform(post("/auth/logout")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
      .startsWith("refreshToken=;")
      .contains("Max-Age=0", "Path=/auth", "HttpOnly", "Secure", "SameSite=Lax");

    mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(token))))
      .andExpect(status().isUnauthorized());
  }

  @Test
  void logout_withNoTokenAnywhere_isIdempotent_andStillClearsTheCookie() throws Exception {
    MvcResult result = mockMvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isOk())
      .andReturn();

    assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
  }

  private RegisterRequest registerRequest() {
    RegisterRequest request = new RegisterRequest();
    request.setFirstName("Cookie");
    request.setLastName("Jar");
    request.setEmail("cookie-" + UUID.randomUUID() + "@test.com");
    request.setPassword(PASSWORD);
    return request;
  }

  private String register() throws Exception {
    RegisterRequest request = registerRequest();
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
      .andExpect(status().isOk());
    return request.getEmail();
  }

  private MvcResult login(String email) throws Exception {
    return mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(email, PASSWORD))))
      .andExpect(status().isOk())
      .andReturn();
  }

  private String cookieValue(MvcResult result) {
    String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
    assertThat(header).as("response must set the refresh cookie").isNotNull();
    return header.substring("refreshToken=".length(), header.indexOf(';'));
  }

  private String bodyRefreshToken(MvcResult result) throws Exception {
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("refreshToken").asText();
  }
}
```

(`security.cors.allowed-origins` is not bound until Task 10; pinning it now keeps this class unchanged later. `RegisterRequest.getEmail()` exists — it is a Lombok `@Getter` DTO, used via `setEmail` in `RefreshTokenFlowIT`.)

- [ ] **Step 7: Run to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.controllers.RefreshCookieIT"`
Expected: FAIL — no `Set-Cookie` header; the no-body calls answer 400.

- [ ] **Step 8: Relax the DTOs**

`RefreshRequest.java`:

```java
package com.enterprise.ordersuite.auth.dtos;

// Optional since Phase 1: the refresh token normally arrives in the HttpOnly cookie.
// The body field is the backward-compatible fallback, removed in Phase 6.
public record RefreshRequest(String refreshToken) {

}
```

`LogoutRequest.java`: the same, with `LogoutRequest` as the name.

- [ ] **Step 9: Wire the controller**

In `AuthenticationController`: add imports `com.enterprise.ordersuite.security.config.RefreshCookieProperties`, `jakarta.servlet.http.HttpServletRequest`, `org.springframework.http.HttpHeaders`, `org.springframework.http.ResponseCookie`; add field `private final RefreshCookieFactory refreshCookieFactory;`; replace the register, login, refresh and logout methods with:

```java
    @Operation(summary = "Register a new user and issue access + refresh tokens (refresh also as HttpOnly cookie)")
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(authenticationService.register(request), httpRequest);
    }

    @Operation(summary = "Login and issue access + refresh tokens (refresh also as HttpOnly cookie)")
    @PostMapping(value= "/login", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request, HttpServletRequest httpRequest) {
        return withRefreshCookie(authenticationService.authenticate(request), httpRequest);
    }

    @Operation(summary = "Rotate the refresh token (cookie, or body as fallback) and issue a new access token")
    @PostMapping(value = "/refresh", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest) {
        String rawToken = RefreshTokenSource.resolve(cookieToken, request == null ? null : request.refreshToken());
        return withRefreshCookie(authenticationService.refresh(rawToken), httpRequest);
    }

    @Operation(summary = "Logout by revoking the refresh token family and clearing the cookie (idempotent)")
    @PostMapping(value = "/logout", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookieProperties.COOKIE_NAME, required = false) String cookieToken,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        authenticationService.logout(RefreshTokenSource.resolve(cookieToken, request == null ? null : request.refreshToken()));
        ResponseCookie cleared = refreshCookieFactory.clear(httpRequest.getContextPath());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).build();
    }

    // The body still carries refreshToken until Phase 6; the cookie is the target transport.
    private ResponseEntity<AuthResponse> withRefreshCookie(AuthResponse response, HttpServletRequest httpRequest) {
        ResponseCookie cookie = refreshCookieFactory.issue(response.getRefreshToken(), httpRequest.getContextPath());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(response);
    }
```

- [ ] **Step 10: Bind the configuration**

In `application.yml`, inside the existing `security:` block (sibling of `rate-limit:`):

```yaml
  refresh-cookie:
    secure: ${REFRESH_COOKIE_SECURE:true}
    same-site: ${REFRESH_COOKIE_SAME_SITE:Lax}
  cors:
    allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000}
```

Append to `.env.example`:

```
# Refresh cookie and CORS (D12). Defaults are production-safe. Local HTTP development must
# set REFRESH_COOKIE_SECURE=false, or the browser drops the cookie on http://localhost.
# CORS_ALLOWED_ORIGINS is comma-separated and must match the frontend origin exactly. It is
# also the Origin allow-list for cookie-borne /auth/refresh and /auth/logout: if you use
# Swagger UI in a browser locally, add http://localhost:8080 or its cookie calls get 403.
REFRESH_COOKIE_SECURE=true
REFRESH_COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=http://localhost:3000
```

- [ ] **Step 11: Run the cookie IT and the rest of auth**

Run: `./gradlew test --tests "com.enterprise.ordersuite.auth.*" --tests "com.enterprise.ordersuite.security.*"`
Expected: PASS — `RefreshCookieIT` (7), `RefreshTokenFlowIT` unchanged and green (body path still works).

- [ ] **Step 12: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/security/config/RefreshCookieProperties.java src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java src/main/java/com/enterprise/ordersuite/auth/controllers src/main/java/com/enterprise/ordersuite/auth/dtos/RefreshRequest.java src/main/java/com/enterprise/ordersuite/auth/dtos/LogoutRequest.java src/main/resources/application.yml .env.example src/test/java/com/enterprise/ordersuite/auth/controllers
git status --short   # must NOT list .env
git commit -m "feat(auth): deliver the refresh token as an HttpOnly cookie, body kept as fallback

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: `Origin` validation and configurable CORS (D12, D19)

**Files:**
- Create: `src/main/java/com/enterprise/ordersuite/security/config/CorsProperties.java`
- Create: `src/main/java/com/enterprise/ordersuite/security/web/RefreshOriginFilter.java`
- Modify: `src/main/java/com/enterprise/ordersuite/security/config/SecurityConfig.java` (`@EnableConfigurationProperties`, `corsConfigurationSource`, `securityFilterChain`)
- Test: `src/test/java/com/enterprise/ordersuite/security/web/RefreshOriginFilterTest.java`, `src/test/java/com/enterprise/ordersuite/security/RefreshOriginIT.java`

**Interfaces:**
- Consumes: `RefreshCookieProperties.COOKIE_NAME` (Task 9), `RequestPaths.withinApplication(HttpServletRequest)` (Task 1, same package — no import), `ApiErrorResponse(String code, String message, Instant timestamp)`.
- Produces: `record CorsProperties(List<String> allowedOrigins)`; `RefreshOriginFilter(List<String> allowedOrigins, ObjectMapper objectMapper, Clock clock)` — not a Spring bean (a `@Component`/`@Bean` filter would also be auto-registered in the servlet chain); instantiated inside `securityFilterChain`.

- [ ] **Step 1: Write the failing filter unit test**

`src/test/java/com/enterprise/ordersuite/security/web/RefreshOriginFilterTest.java`:

```java
package com.enterprise.ordersuite.security.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshOriginFilterTest {

  private static final String FRONTEND = "http://localhost:3000";

  private final RefreshOriginFilter filter = new RefreshOriginFilter(
    List.of(FRONTEND),
    new ObjectMapper().registerModule(new JavaTimeModule()),
    Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void cookieWithAllowedOrigin_passes() throws Exception {
    MockHttpServletResponse response = run(request("/auth/refresh", true, FRONTEND));
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void cookieWithoutOrigin_isRejected() throws Exception {
    MockHttpServletResponse response = run(request("/auth/refresh", true, null));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"code\":\"ORIGIN_NOT_ALLOWED\"");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://evil.example", "http://localhost:3000/", "http://localhost:3001", "null"})
  void cookieWithAnyOtherOrigin_isRejected(String origin) throws Exception {
    assertThat(run(request("/auth/logout", true, origin)).getStatus())
      .as("exact match only - '%s' is not the frontend", origin)
      .isEqualTo(403);
  }

  @Test
  void noCookie_skipsTheCheck_evenWithAForeignOrigin() throws Exception {
    assertThat(run(request("/auth/refresh", false, "https://evil.example")).getStatus())
      .as("a body-borne token was already readable by the caller, so CSRF does not apply")
      .isEqualTo(200);
  }

  @Test
  void otherAuthEndpoints_areNotGuarded() throws Exception {
    assertThat(run(request("/auth/login", true, "https://evil.example")).getStatus()).isEqualTo(200);
  }

  @Test
  void guardedPath_isMatchedUnderAContextPath() throws Exception {
    MockHttpServletRequest request = request("/api/auth/refresh", true, null);
    request.setContextPath("/api");

    assertThat(run(request).getStatus())
      .as("production serves under /api; the guard must not silently switch off there")
      .isEqualTo(403);
  }

  private MockHttpServletRequest request(String uri, boolean withCookie, String origin) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
    if (withCookie) {
      request.setCookies(new Cookie("refreshToken", "raw"));
    }
    if (origin != null) {
      request.addHeader("Origin", origin);
    }
    return request;
  }

  private MockHttpServletResponse run(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
```

(`@ParameterizedTest` comes with `spring-boot-starter-test`, already a test dependency.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.web.RefreshOriginFilterTest"`
Expected: FAIL — `RefreshOriginFilter` does not exist.

- [ ] **Step 3: Implement properties and filter**

`CorsProperties.java`:

```java
package com.enterprise.ordersuite.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

// D12. Comma-separated in the environment; also the allow-list for RefreshOriginFilter.
@ConfigurationProperties(prefix = "security.cors")
public record CorsProperties(
    @DefaultValue("http://localhost:3000") List<String> allowedOrigins
) {
}
```

`RefreshOriginFilter.java`:

```java
package com.enterprise.ordersuite.security.web;

import com.enterprise.ordersuite.api.errors.ApiErrorResponse;
import com.enterprise.ordersuite.security.config.RefreshCookieProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * CSRF defense for the only two cookie-authenticated endpoints (D19). A request carrying the
 * refresh cookie must come from an allowed origin. Requests without the cookie are not
 * CSRF-able - the caller had to read the token to send it - so they pass untouched.
 * Not authorization: it never looks at who the user is.
 */
public class RefreshOriginFilter extends OncePerRequestFilter {

    private static final Set<String> GUARDED_PATHS = Set.of("/auth/refresh", "/auth/logout");

    private final List<String> allowedOrigins;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RefreshOriginFilter(List<String> allowedOrigins, ObjectMapper objectMapper, Clock clock) {
        this.allowedOrigins = List.copyOf(allowedOrigins);
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !GUARDED_PATHS.contains(RequestPaths.withinApplication(request)) || !hasRefreshCookie(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && allowedOrigins.contains(origin)) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                new ApiErrorResponse("ORIGIN_NOT_ALLOWED", "Origin not allowed", Instant.now(clock)));
    }

    private static boolean hasRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies != null && Arrays.stream(cookies)
                .anyMatch(c -> RefreshCookieProperties.COOKIE_NAME.equals(c.getName()));
    }
}
```

- [ ] **Step 4: Run the unit test**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.web.RefreshOriginFilterTest"`
Expected: PASS (10 cases incl. parameterized).

- [ ] **Step 5: Write the failing IT**

`src/test/java/com/enterprise/ordersuite/security/RefreshOriginIT.java`:

```java
package com.enterprise.ordersuite.security;

import com.enterprise.ordersuite.auth.dtos.AuthRequest;
import com.enterprise.ordersuite.auth.dtos.RefreshRequest;
import com.enterprise.ordersuite.auth.dtos.RegisterRequest;
import com.enterprise.ordersuite.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "security.cors.allowed-origins=http://localhost:3000")
class RefreshOriginIT {

  private static final String FRONTEND = "http://localhost:3000";
  private static final String EVIL = "https://evil.example";
  private static final String PASSWORD = "Password123!";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Test
  void refresh_cookieWithoutOrigin_returns403_andDoesNotConsumeTheToken() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(cookieRefresh(token))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_cookieFromAForeignOrigin_returns403_andDoesNotConsumeTheToken() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, EVIL))
      .andExpect(status().isForbidden())
      .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void logout_cookieFromAForeignOrigin_returns403_andLeavesTheSessionAlive() throws Exception {
    String token = loginForRefreshToken();

    mockMvc.perform(post("/auth/logout")
        .cookie(new Cookie("refreshToken", token))
        .header(HttpHeaders.ORIGIN, EVIL)
        .contentType(MediaType.APPLICATION_JSON))
      .andExpect(status().isForbidden());

    mockMvc.perform(cookieRefresh(token).header(HttpHeaders.ORIGIN, FRONTEND))
      .andExpect(status().isOk());
  }

  @Test
  void refresh_bodyTokenWithoutOrigin_stillWorks() throws Exception {
    mockMvc.perform(post("/auth/refresh")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new RefreshRequest(loginForRefreshToken()))))
      .andExpect(status().isOk());
  }

  @Test
  void preflight_fromTheFrontend_allowsCredentials() throws Exception {
    mockMvc.perform(options("/auth/refresh")
        .header(HttpHeaders.ORIGIN, FRONTEND)
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
      .andExpect(status().isOk())
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND))
      .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
  }

  private MockHttpServletRequestBuilder cookieRefresh(String token) {
    return post("/auth/refresh")
      .cookie(new Cookie("refreshToken", token))
      .contentType(MediaType.APPLICATION_JSON);
  }

  private String loginForRefreshToken() throws Exception {
    RegisterRequest register = new RegisterRequest();
    register.setFirstName("Origin");
    register.setLastName("Check");
    register.setEmail("origin-" + UUID.randomUUID() + "@test.com");
    register.setPassword(PASSWORD);
    mockMvc.perform(post("/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(register)))
      .andExpect(status().isOk());

    String body = mockMvc.perform(post("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new AuthRequest(register.getEmail(), PASSWORD))))
      .andExpect(status().isOk())
      .andReturn().getResponse().getContentAsString();
    return objectMapper.readTree(body).get("refreshToken").asText();
  }
}
```

- [ ] **Step 6: Run to verify it fails**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.RefreshOriginIT"`
Expected: FAIL — the missing-Origin case answers 200 (no filter yet); the foreign-origin case is rejected by Spring's CORS processor with a non-JSON 403, so the `$.code` assertion fails.

- [ ] **Step 7: Wire it into `SecurityConfig`**

1. Annotation: `@EnableConfigurationProperties({RateLimitProperties.class, RefreshCookieProperties.class, CorsProperties.class})`.
2. Add field `private final CorsProperties corsProperties;` and imports `com.enterprise.ordersuite.security.web.RefreshOriginFilter`, `org.springframework.web.filter.CorsFilter`.
3. In `corsConfigurationSource()`, replace the hardcoded line with:

```java
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
```

4. In `securityFilterChain`, after `.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);` add a new statement:

```java
    // Ahead of CorsFilter, so a foreign origin gets the standard ApiErrorResponse (403
    // ORIGIN_NOT_ALLOWED) rather than the CORS processor's plain-text rejection. Not a bean:
    // a Filter bean would also be auto-registered in the servlet chain.
    http.addFilterBefore(
      new RefreshOriginFilter(corsProperties.allowedOrigins(), objectMapper, clock),
      CorsFilter.class);
```

- [ ] **Step 8: Run the IT and the security suite**

Run: `./gradlew test --tests "com.enterprise.ordersuite.security.*" --tests "com.enterprise.ordersuite.auth.*"`
Expected: PASS — including `RefreshCookieIT` (its cookie calls send `Origin: http://localhost:3000`).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/enterprise/ordersuite/security src/test/java/com/enterprise/ordersuite/security
git commit -m "feat(security): validate Origin on cookie-borne refresh and logout; CORS from config

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Verification, review and records

**Files:**
- Modify: `docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md` (Status line)
- Modify: `.claude/skills/spring-security-changes/SKILL.md` ("Target model — not yet built")

- [ ] **Step 1: Full suite**

Run: `./gradlew test` (Docker running).
Expected: 0 failures. Record the total (Phase 0 ended at 223; this plan adds roughly 50). If anything fails, stop and use `superpowers:systematic-debugging` — do not adjust assertions to match.

- [ ] **Step 2: Security review**

Dispatch the `spring-security-reviewer` agent on the diff `e611751..HEAD`, asking specifically about: `RequestPaths` and every remaining path match (no raw `getRequestURI()` comparisons), the `server.forward-headers-strategy` default, the `noRollbackFor` reuse path, cleanup no longer deleting used tokens early, the `PESSIMISTIC_WRITE` lookup, `RefreshOriginFilter` ordering relative to `CorsFilter`, cookie attributes, and that nothing sensitive entered the JWT. Fix every confirmed finding in its own commit, re-running `./gradlew test`.

- [ ] **Step 3: Update the security skill's target section**

In `.claude/skills/spring-security-changes/SKILL.md`, under "Target model — not yet built", mark the refresh cookie, rotation with family revocation, the Origin check and the three JWT claims as **built in Phase 1 (backward-compatible)**, and state what remains for Phase 6: drop `refreshToken` from `AuthResponse`, remove the body fallback, making the Origin check universal. Update the filter-chain line in "The map" to include `RefreshOriginFilter` (before `CorsFilter`). Keep the `/public/*` bullet unchanged. Add a hard rule so the rate-limit defect cannot recur:

```markdown
### 7. Filters match the path within the application, and are tested under `/api`.

`getRequestURI()` includes the context path (`SERVER_CONTEXT_PATH`, `/api` in every real
deployment); MockMvc sends none. A filter comparing the raw URI passes every test and is
switched off in production — `AuthRateLimitFilter` shipped that way until Phase 1. Match with
`security.web.RequestPaths.withinApplication(request)`, and give every path-matching filter
at least one test that sets `contextPath("/api")`.
```

In `.claude/skills/writing-backend-tests/SKILL.md`, under "Conventions", add one bullet: `**Context path.** MockMvc sends no context path; production serves under /api. A test of anything that matches on the request path (filters, cookie paths) must include a case with .contextPath("/api") — see security/ratelimit/ContextPathRateLimitIT.java.` Add that file to this step's `git add`.

- [ ] **Step 4: Update the spec status**

Change the spec's `Status:` line to `implemented — see ../plans/2026-09-25-restaurant-ops-phase-1-auth.md`.

- [ ] **Step 5: Commit**

```bash
git add .claude/skills/spring-security-changes/SKILL.md .claude/skills/writing-backend-tests/SKILL.md docs/superpowers/specs/2026-09-25-restaurant-ops-phase-1-auth-design.md
git commit -m "docs: record Phase 1 auth as implemented

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 6: Hand-offs for Gabriel (report, do not do)**

- Add `REFRESH_COOKIE_SECURE=false` (and optionally the other two keys) to the local `.env` — it is not read or edited by the agent.
- Restart the local app (it is running the pre-Phase 1 build), then three manual checks — the first two are the only proof of D22/D23 inside a real Tomcat, which MockMvc cannot give:
  1. Seven bad logins answer 429 from the sixth on (needs `RATE_LIMIT_ENABLED` not `false` locally):
     `for i in 1 2 3 4 5 6 7; do curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" -d '{"email":"probe@nowhere.test","password":"wrong"}'; done`
  2. Wait a minute for the window to refill, then repeat with a different forged header each time — still 429 on the sixth:
     `for i in 1 2 3 4 5 6 7; do curl -s -o /dev/null -w "%{http_code}\n" -X POST http://localhost:8080/api/auth/login -H "X-Forwarded-For: 203.0.113.$i" -H "Content-Type: application/json" -d '{"email":"probe2@nowhere.test","password":"wrong"}'; done`
  3. Log in from `http://localhost:3000` and confirm the `refreshToken` cookie in devtools (HttpOnly, `Path=/api/auth`).
- Before any production deployment: set `SERVER_FORWARD_HEADERS_STRATEGY=native` if a reverse proxy or load balancer sits in front (spec D23).
- Frontend follow-ups (spec, "Contract work"): `credentials: 'include'` on `/auth/*`; single-flight `/auth/refresh` across tabs; never retry a 401 from `/auth/refresh`; read `firstName`/`lastName`/`email` from the token.
