package com.enterprise.ordersuite.common.persistence;

import com.github.f4b6a3.ulid.UlidCreator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;

import java.util.EnumSet;

import static org.hibernate.generator.EventTypeSets.INSERT_ONLY;

// Monotonic: ids created in the same millisecond still sort in creation order.
public class UlidIdGenerator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        return UlidCreator.getMonotonicUlid().toString();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return INSERT_ONLY;
    }
}
