package com.enterprise.ordersuite.identity.application.mapper;

import com.enterprise.ordersuite.identity.api.dto.IdentityAuditEventResponse;
import com.enterprise.ordersuite.identity.domain.IdentityAuditEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class IdentityAuditMapper {

    private final ObjectMapper objectMapper;

    public IdentityAuditMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public IdentityAuditEventResponse toResponse(IdentityAuditEvent event) {
        JsonNode metadataNode = null;

        if (event.getMetadata() != null && !event.getMetadata().isBlank()) {
            try {
                metadataNode = objectMapper.readTree(event.getMetadata());
            } catch (Exception e) {
                metadataNode = objectMapper.getNodeFactory().textNode(event.getMetadata());
            }
        }

        return new IdentityAuditEventResponse(
                event.getId(),
                event.getType().name(),
                event.getActorUserId(),
                event.getTargetUserId(),
                metadataNode,
                event.getCreatedAt()
        );
    }
}