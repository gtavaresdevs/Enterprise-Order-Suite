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
