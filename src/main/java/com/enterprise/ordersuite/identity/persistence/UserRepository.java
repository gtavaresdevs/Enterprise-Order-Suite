package com.enterprise.ordersuite.identity.persistence;

import com.enterprise.ordersuite.identity.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    @Query("""
            select u
            from User u
            join fetch u.role
            where lower(u.email) = lower(:email)
            """)
    Optional<User> findByEmailIgnoreCaseFetchingRole(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String normalizedEmail);
}
