package com.enterprise.ordersuite.auth.domain;

import com.enterprise.ordersuite.common.persistence.UlidId;
import com.enterprise.ordersuite.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "password_history")
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistory {

  @Id
  @UlidId
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 26)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PasswordHistory(User user, String passwordHash, Instant createdAt) {
    this.user = user;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }
}
