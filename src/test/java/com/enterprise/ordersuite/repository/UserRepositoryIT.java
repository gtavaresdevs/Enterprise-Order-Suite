package com.enterprise.ordersuite.repository;

import com.enterprise.ordersuite.entities.role.Role;
import com.enterprise.ordersuite.entities.user.User;
import com.enterprise.ordersuite.repositories.RoleRepository;
import com.enterprise.ordersuite.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends AbstractPostgresRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Role userRole() {
        return roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Expected role USER to exist from Flyway seed"));
    }

    private User newValidUser(String email, String password) {
        User u = new User();
        u.setFirstName("Gabriel");
        u.setLastName("Almeida");
        u.setEmail(email);
        u.setPassword(password);
        u.setActive(true);
        u.setRole(userRole());
        return u;
    }

    @Test
    void findByEmail_works() {
        userRepository.saveAndFlush(newValidUser("gabriel@example.com", "encoded"));

        var found = userRepository.findByEmail("gabriel@example.com");
        assertTrue(found.isPresent());
        assertEquals("gabriel@example.com", found.get().getEmail());
    }

    @Test
    void uniqueEmailConstraint_enforced() {
        userRepository.saveAndFlush(newValidUser("dup@example.com", "encoded1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(newValidUser("dup@example.com", "encoded2")));
    }
}
