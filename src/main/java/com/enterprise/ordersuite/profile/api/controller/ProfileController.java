package com.enterprise.ordersuite.profile.api.controller;

import com.enterprise.ordersuite.profile.api.dto.ProfileResponse;
import com.enterprise.ordersuite.profile.api.dto.UpdateProfileRequest;
import com.enterprise.ordersuite.profile.application.service.ProfileService;
import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
@RequiredArgsConstructor
public class ProfileController {

  private final ProfileService profileService;

  @GetMapping
  public ResponseEntity<ProfileResponse> getProfile(
    @AuthenticationPrincipal JwtUserPrincipal principal
  ) {
    ProfileResponse response =
      profileService.getProfile(principal.id());

    return ResponseEntity.ok(response);
  }

  @PatchMapping
  public ResponseEntity<ProfileResponse> updateProfile(
    @AuthenticationPrincipal JwtUserPrincipal principal,
    @RequestBody UpdateProfileRequest request
  ) {
    ProfileResponse response =
      profileService.updateProfile(
        principal.id(),
        request
      );

    return ResponseEntity.ok(response);
  }
}
