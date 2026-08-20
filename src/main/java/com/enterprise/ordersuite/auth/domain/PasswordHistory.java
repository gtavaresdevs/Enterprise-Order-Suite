package com.enterprise.ordersuite.auth.domain;

import com.enterprise.ordersuite.identity.domain.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "password_history")
@Getter
@Setter
@NoArgsConstructor
public class PasswordHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  public PasswordHistory(User user, String passwordHash, LocalDateTime createdAt) {
    this.user = user;
    this.passwordHash = passwordHash;
    this.createdAt = createdAt;
  }
}
