package com.enterprise.ordersuite;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;

// @IntegrationTest, not a bare @SpringBootTest: without the Testcontainers configs this
// booted against whatever database .env pointed at, so it passed or failed on the state of
// a developer's local Postgres rather than on the code. Editing V15 is what exposed it.
@IntegrationTest
class EnterpriseOrderSuiteApplicationTests {

  @Test
  void contextLoads() {}
}
