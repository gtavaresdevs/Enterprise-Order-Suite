package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.PagedResponse;
import com.enterprise.ordersuite.identity.api.dto.UserDetailResponse;
import com.enterprise.ordersuite.identity.api.dto.UserSummaryResponse;
import com.enterprise.ordersuite.identity.application.UserQueryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class UsersController {

    private final UserQueryService userQueryService;

    public UsersController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users")
    public PagedResponse<UserSummaryResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return userQueryService.listUsers(page, size);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/users/{id}")
    public UserDetailResponse get(@PathVariable("id") long id) {
        return userQueryService.getUser(id);
    }
}