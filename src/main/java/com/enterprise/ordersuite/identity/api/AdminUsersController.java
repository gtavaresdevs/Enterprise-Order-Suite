package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.*;
import com.enterprise.ordersuite.identity.application.UserAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminUsersController {

    private final UserAdminService userAdminService;

    public AdminUsersController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/deactivate")
    public ResponseEntity<UserStatusResponse> deactivate(@PathVariable("id") long id) {
        return ResponseEntity.ok(userAdminService.deactivateUser(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/reactivate")
    public ResponseEntity<UserStatusResponse> reactivate(@PathVariable("id") long id) {
        return ResponseEntity.ok(userAdminService.reactivateUser(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/users/{id}/status")
    public ResponseEntity<UserStatusResponse> status(@PathVariable("id") long id) {
        return ResponseEntity.ok(userAdminService.getStatus(id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/users/{id}/role")
    public ResponseEntity<UserStatusResponse> setRole(
            @PathVariable("id") long id,
            @Valid @RequestBody SetUserRoleRequest request
    ) {
        return ResponseEntity.ok(userAdminService.setUserRole(id, request));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users")
    public ResponseEntity<AdminCreateUserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        AdminCreateUserResponse response = userAdminService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/users/{id}/password-setup")
    public ResponseEntity<Void> resendPasswordSetup(@PathVariable("id") long id) {
        userAdminService.sendPasswordSetup(id);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/admin/users/{id}")
    public ResponseEntity<AdminUpdateUserResponse> updateUser(@PathVariable("id") long id, @Valid @RequestBody AdminUpdateUserRequest request){
        return ResponseEntity.ok(userAdminService.updateUser(id,request));
    }
}