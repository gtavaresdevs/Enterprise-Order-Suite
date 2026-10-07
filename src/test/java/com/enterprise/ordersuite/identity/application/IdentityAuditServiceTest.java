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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityAuditServiceTest {

  private static final String RESTAURANT = "01J0000000000000000000000R";
  private static final String ACTOR = "01J0000000000000000000000A";
  private static final String TARGET = "01J0000000000000000000000T";

  @Mock
  private IdentityAuditEventRepository repository;

  @InjectMocks
  private IdentityAuditService service;

  @Test
  void recordEvent_persistsAuditEventWithCorrectData() {
    when(repository.save(org.mockito.ArgumentMatchers.any(IdentityAuditEvent.class)))
      .thenAnswer(invocation -> invocation.getArgument(0));

    IdentityAuditEvent result = service.recordEvent(
      IdentityAuditEventType.ROLE_CHANGED,
      RESTAURANT,
      ACTOR,
      TARGET,
      Map.of("from", "STAFF", "to", "MANAGER")
    );

    ArgumentCaptor<IdentityAuditEvent> captor =
      ArgumentCaptor.forClass(IdentityAuditEvent.class);

    verify(repository).save(captor.capture());

    IdentityAuditEvent persistedEvent = captor.getValue();

    assertThat(persistedEvent.getType())
      .isEqualTo(IdentityAuditEventType.ROLE_CHANGED);

    assertThat(persistedEvent.getRestaurantId())
      .isEqualTo(RESTAURANT);

    assertThat(persistedEvent.getActorUserId())
      .isEqualTo(ACTOR);

    assertThat(persistedEvent.getTargetUserId())
      .isEqualTo(TARGET);

    assertThat(persistedEvent.getDetails())
      .containsEntry("from", "STAFF")
      .containsEntry("to", "MANAGER");

    assertThat(result)
      .isSameAs(persistedEvent);
  }
}
