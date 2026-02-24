package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.IdentityAuditEventResponse;
import com.enterprise.ordersuite.identity.api.dto.PagedResponse;
import com.enterprise.ordersuite.identity.application.mapper.IdentityAuditMapper;
import com.enterprise.ordersuite.identity.persistence.IdentityAuditEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityAuditQueryService {

    private final IdentityAuditEventRepository repo;
    private final IdentityAuditMapper mapper;

    public IdentityAuditQueryService(IdentityAuditEventRepository repo, IdentityAuditMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<IdentityAuditEventResponse> list(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
        var result = repo.findAll(pageable);

        var items = result.getContent().stream().map(mapper::toResponse).toList();

        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }
}