package com.enterprise.ordersuite.identity.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

// A user's place in one restaurant (D-2: at most one per user). The restaurant is held by
// id: the restaurants module owns that entity.
@Entity
@Table(name = "memberships")
@Getter
@Setter
@NoArgsConstructor
public class Membership extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "restaurant_id", nullable = false, length = 26)
    private String restaurantId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipRole role;

    public Membership(String restaurantId, User user, MembershipRole role) {
        this.restaurantId = restaurantId;
        this.user = user;
        this.role = role;
    }
}
