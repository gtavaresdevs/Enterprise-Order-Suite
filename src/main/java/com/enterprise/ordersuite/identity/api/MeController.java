package com.enterprise.ordersuite.identity.api;

import com.enterprise.ordersuite.identity.api.dto.MeResponse;
import com.enterprise.ordersuite.identity.application.MeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    private final MeService meService;

    public MeController(MeService meService) {
        this.meService = meService;
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me() {
        return ResponseEntity.ok(meService.getMe());
    }
}
