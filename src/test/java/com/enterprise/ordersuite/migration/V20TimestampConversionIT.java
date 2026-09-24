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
