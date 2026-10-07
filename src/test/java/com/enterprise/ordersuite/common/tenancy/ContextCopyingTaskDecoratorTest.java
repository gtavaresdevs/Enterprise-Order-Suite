package com.enterprise.ordersuite.common.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ContextCopyingTaskDecoratorTest {

  private static final String RESTAURANT = "01J0000000000000000000000R";
  private static final String USER = "01J0000000000000000000000U";

  private final ContextCopyingTaskDecorator decorator = new ContextCopyingTaskDecorator();

  @AfterEach
  void clear() {
    TenantContextHolder.clear();
    MDC.clear();
  }

  @Test
  void decorate_copiesTenantContextAndMdc_ontoTheWorkerThread() throws Exception {
    TenantContext context = new TenantContext(RESTAURANT, USER, "OWNER", false);
    TenantContextHolder.set(context);
    MDC.put("restaurantId", RESTAURANT);
    AtomicReference<TenantContext> seenContext = new AtomicReference<>();
    AtomicReference<String> seenMdc = new AtomicReference<>();

    Runnable task = decorator.decorate(() -> {
      seenContext.set(TenantContextHolder.get().orElse(null));
      seenMdc.set(MDC.get("restaurantId"));
    });
    Thread worker = new Thread(task);
    worker.start();
    worker.join();

    assertThat(seenContext.get()).isEqualTo(context);
    assertThat(seenMdc.get()).isEqualTo(RESTAURANT);
  }

  @Test
  void decorate_leavesThePooledThreadClean_afterTheTask() throws Exception {
    TenantContextHolder.set(new TenantContext(RESTAURANT, USER, "OWNER", false));
    MDC.put("restaurantId", RESTAURANT);
    Runnable task = decorator.decorate(() -> {
    });
    AtomicReference<Boolean> leaked = new AtomicReference<>();

    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      pool.submit(task).get();
      pool.submit(() -> leaked.set(TenantContextHolder.get().isPresent() || MDC.get("restaurantId") != null)).get();
    } finally {
      pool.shutdown();
    }

    assertThat(leaked.get()).as("a later task on the same pooled thread must not inherit the restaurant").isFalse();
  }
}
