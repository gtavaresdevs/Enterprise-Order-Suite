package com.enterprise.ordersuite.repository;

import com.enterprise.ordersuite.identity.domain.Role;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
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

  @Autowired
  private RoleRepository roleRepository;

  private Role userRole() {
    return roleRepository.findByName("USER")
      .orElseThrow(() ->
        new IllegalStateException("Expected role USER to exist from Flyway seed"));
  }

  private User newValidUser(String email, String password) {
    User user = new User();
    user.setFirstName("Test");
    user.setLastName("User");
    user.setEmail(email);
    user.setPassword(password);
    user.setActive(true);
    user.setRole(userRole());
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
