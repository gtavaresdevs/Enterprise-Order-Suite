package com.enterprise.ordersuite.profile.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile extends BaseEntity {

  @Column(name = "user_id", nullable = false, unique = true)
  private Long userId;

  @Column(length = 50)
  private String phone;

  @Column(length = 100)
  private String country;

  @Column(length = 100)
  private String timezone;

  @Column(length = 150)
  private String department;

  @Column(length = 150)
  private String office;

  @Column(columnDefinition = "TEXT")
  private String bio;

  @Column(name = "avatar_key", length = 255)
  private String avatarKey;

  public UserProfile(Long userId) {
    this.userId = userId;
  }

  public void update(
    String phone,
    String country,
    String timezone,
    String department,
    String office,
    String bio
  ) {
    this.phone = phone;
    this.country = country;
    this.timezone = timezone;
    this.department = department;
    this.office = office;
    this.bio = bio;
  }
}
