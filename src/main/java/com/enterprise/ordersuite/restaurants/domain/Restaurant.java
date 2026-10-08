package com.enterprise.ordersuite.restaurants.domain;

import com.enterprise.ordersuite.common.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// The tenant (ADR-0001, Tenancy & Identity D-1).
@Entity
@Table(name = "restaurants")
@Getter
@Setter
@NoArgsConstructor
public class Restaurant extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    // Unique and never changes this run (D-7).
    @Column(nullable = false, unique = true, length = 40, updatable = false)
    private String slug;

    // IANA zone name (D-9).
    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.BRL;

    public Restaurant(String name, String slug, String timezone) {
        this.name = name;
        this.slug = slug;
        this.timezone = timezone;
    }
}
