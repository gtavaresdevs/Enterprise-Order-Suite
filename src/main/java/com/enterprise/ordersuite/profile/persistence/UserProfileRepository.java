package com.enterprise.ordersuite.profile.persistence;

import com.enterprise.ordersuite.profile.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

  Optional<UserProfile> findByUserId(Long userId);
}
