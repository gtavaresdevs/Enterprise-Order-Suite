package com.enterprise.ordersuite.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@MappedSuperclass
public abstract class BaseEntity {
    // ULID (ADR-0009): null until the first persist, so Spring Data still sees a new entity.
    @Id
    @UlidId
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 26)
    protected String id;

    @CreationTimestamp
    protected Instant createdAt;

    @UpdateTimestamp
    protected Instant updatedAt;
}
