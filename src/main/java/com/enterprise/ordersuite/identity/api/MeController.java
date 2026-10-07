package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.MeResponse;
import com.enterprise.ordersuite.identity.api.dto.UpdateMeRequest;
import com.enterprise.ordersuite.identity.application.MeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

// Own account (§5.3): every signed-in user, whatever the role.
@RestController
@PreAuthorize("isAuthenticated()")
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(meService.getMe());
    }

    @PatchMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MeResponse> updateMe(@Valid @RequestBody UpdateMeRequest request) {
        return ResponseEntity.ok(meService.updateMe(request));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MeResponse> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(meService.uploadAvatar(file));
    }

    @DeleteMapping("/me/avatar")
    public ResponseEntity<Void> deleteAvatar() {
        meService.deleteAvatar();
        return ResponseEntity.noContent().build();
    }
}
