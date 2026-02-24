package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.RoleResponse;
import com.enterprise.ordersuite.identity.application.mapper.RoleMapper;
import com.enterprise.ordersuite.identity.persistence.RoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleQueryService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    public RoleQueryService(RoleRepository roleRepository, RoleMapper roleMapper) {
        this.roleRepository = roleRepository;
        this.roleMapper = roleMapper;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roleRepository.findAll().stream().map(roleMapper::toResponse).toList();
    }
}