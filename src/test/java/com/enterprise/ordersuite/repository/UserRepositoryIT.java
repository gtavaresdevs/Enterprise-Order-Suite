package com.enterprise.ordersuite.repository;

import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@IntegrationTest
class UserRepositoryIT {

  @Autowired
  private UserRepository userRepository;

  private User newValidUser(String email, String password) {
    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail(email);
    user.setPassword(password);
    user.setActive(true);
    return user;
  }

  @Test
  void findByEmail_works() {
    String email = "repository-" + UUID.randomUUID() + "@test.com";

    userRepository.saveAndFlush(
      newValidUser(email, "encoded")
    );

    var found = userRepository.findByEmail(email);

    assertTrue(found.isPresent());
    assertEquals(email, found.get().getEmail());
    assertTrue(found.get().getId().matches("[0-9A-HJKMNP-TV-Z]{26}"),
      "ADR-0009: the id is a ULID in Crockford Base32");
  }

  @Test
  void uniqueEmailConstraint_enforced() {
    String email = "duplicate-" + UUID.randomUUID() + "@test.com";

    userRepository.saveAndFlush(
      newValidUser(email, "encoded1")
    );

    assertThrows(
      DataIntegrityViolationException.class,
      () -> userRepository.saveAndFlush(
        newValidUser(email, "encoded2")
      )
    );
  }
}
