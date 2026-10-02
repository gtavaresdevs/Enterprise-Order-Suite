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
