package com.enterprise.ordersuite.profile.api.controller;

import com.enterprise.ordersuite.profile.api.dto.ProfileResponse;
import com.enterprise.ordersuite.profile.api.dto.UpdateProfileRequest;
import com.enterprise.ordersuite.profile.application.service.ProfileService;
import com.enterprise.ordersuite.security.userdetails.JwtUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("me/profile")
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

  @PostMapping(
    value = "/avatar",
    consumes = MediaType.MULTIPART_FORM_DATA_VALUE
  )
  public ResponseEntity<ProfileResponse> uploadAvatar(
    @AuthenticationPrincipal JwtUserPrincipal principal,
    @RequestPart("file") MultipartFile file
  ) {
    ProfileResponse response =
      profileService.uploadAvatar(
        principal.id(),
        file
      );

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/avatar")
  public ResponseEntity<ProfileResponse> deleteAvatar(
    @AuthenticationPrincipal JwtUserPrincipal principal
  ) {
    ProfileResponse response =
      profileService.deleteAvatar(principal.id());

    return ResponseEntity.ok(response);
  }
}
