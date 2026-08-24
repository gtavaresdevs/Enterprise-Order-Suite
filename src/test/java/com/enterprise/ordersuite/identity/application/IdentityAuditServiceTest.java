package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEventType;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuditServiceTest {

  @Mock
  private IdentityAuditEventRepository repository;

  @InjectMocks
  private IdentityAuditService service;

  @Test
  void recordEvent_persistsAuditEventWithCorrectData() {
    when(repository.save(org.mockito.ArgumentMatchers.any(IdentityAuditEvent.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    IdentityAuditEvent result = service.recordEvent(
      IdentityAuditEventType.USER_DEACTIVATED,
      10L,
      20L,
      "{\"reason\":\"policy\"}"
    );

    ArgumentCaptor<IdentityAuditEvent> captor =
      ArgumentCaptor.forClass(IdentityAuditEvent.class);

    verify(repository).save(captor.capture());

    IdentityAuditEvent persistedEvent = captor.getValue();

    assertThat(persistedEvent.getType())
      .isEqualTo(IdentityAuditEventType.USER_DEACTIVATED);

    assertThat(persistedEvent.getActorUserId())
      .isEqualTo(10L);

    assertThat(persistedEvent.getTargetUserId())
      .isEqualTo(20L);

    assertThat(persistedEvent.getMetadata())
      .isEqualTo("{\"reason\":\"policy\"}");

    assertThat(result)
      .isSameAs(persistedEvent);
  }
}
