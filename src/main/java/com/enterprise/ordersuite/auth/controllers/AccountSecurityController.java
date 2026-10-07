package com.enterprise.ordersuite.auth.controllers;

import com.enterprise.ordersuite.auth.dtos.ChangePasswordRequest;
import com.enterprise.ordersuite.auth.service.AccountSecurityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Own account (§5.3): every signed-in user, whatever the role.
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Me", description = "The signed-in user's own account")
public class AccountSecurityController {

    private final AccountSecurityService accountSecurityService;

    @Operation(summary = "Change the password; every other session of the user is signed out")
    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        accountSecurityService.changePassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Sign out every other session of the user; the current one continues")
    @PostMapping("/sign-out-other-devices")
    public ResponseEntity<Void> signOutOtherDevices() {
        accountSecurityService.signOutOtherDevices();
        return ResponseEntity.noContent().build();
    }
}
