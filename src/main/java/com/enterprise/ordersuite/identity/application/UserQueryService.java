package com.enterprise.ordersuite.identity.application;

import com.enterprise.ordersuite.identity.api.dto.PagedResponse;
import com.enterprise.ordersuite.identity.api.dto.UserDetailResponse;
import com.enterprise.ordersuite.identity.api.dto.UserSummaryResponse;
import com.enterprise.ordersuite.identity.application.mapper.UserMapper;
import com.enterprise.ordersuite.identity.domain.User;
import com.enterprise.ordersuite.identity.persistence.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserQueryService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserQueryService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PagedResponse<UserSummaryResponse> listUsers(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        var result = userRepository.findAll(pageable);

        var items = result.getContent().stream().map(userMapper::toSummary).toList();

        return new PagedResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUser(long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return userMapper.toDetail(u);
    }
}