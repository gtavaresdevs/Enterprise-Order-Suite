package com.enterprise.ordersuite.identity.persistence;
import com.enterprise.ordersuite.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);

}