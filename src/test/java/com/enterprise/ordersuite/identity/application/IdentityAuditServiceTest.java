package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IdentityAuditServiceTest {

    @Test
    void recordEvent_persistsAuditEventWithCorrectData() {
        IdentityAuditEventRepository repo = mock(IdentityAuditEventRepository.class);

        when(repo.save(any(IdentityAuditEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        IdentityAuditService service = new IdentityAuditService(repo);

        IdentityAuditEvent event = service.recordEvent(
                IdentityAuditEventType.USER_DEACTIVATED,
                10L,
                20L,
                "{\"reason\":\"policy\"}"
        );

        assertThat(event.getType()).isEqualTo(IdentityAuditEventType.USER_DEACTIVATED);
        assertThat(event.getActorUserId()).isEqualTo(10L);
        assertThat(event.getTargetUserId()).isEqualTo(20L);
        assertThat(event.getMetadata()).contains("policy");

        verify(repo, times(1)).save(any(IdentityAuditEvent.class));
    }
}
