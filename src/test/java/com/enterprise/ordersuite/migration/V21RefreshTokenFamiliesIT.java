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
