package com.enterprise.ordersuite.auth.persistence;

import com.enterprise.ordersuite.auth.domain.PasswordHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PasswordHistoryRepository extends JpaRepository<PasswordHistory, Long> {

  // Fetch chronological history matching the most recent entries first
  @Query("SELECT ph FROM PasswordHistory ph WHERE ph.user.id = :userId ORDER BY ph.createdAt DESC")
  List<PasswordHistory> findRecentByUserId(@Param("userId") Long userId, Pageable pageable);

  // Enterprise Best Practice: High-performance bulk delete via a subquery strategy.
  // Removes records for a specific user that are not within the top N newest records.
  @Modifying
  @Query("DELETE FROM PasswordHistory ph WHERE ph.user.id = :userId AND ph.id NOT IN (" +
    "SELECT sub.id FROM PasswordHistory sub WHERE sub.user.id = :userId ORDER BY sub.createdAt DESC LIMIT :limit" +
    ")")
  void pruneOldEntries(@Param("userId") Long userId, @Param("limit") long limit);
}
