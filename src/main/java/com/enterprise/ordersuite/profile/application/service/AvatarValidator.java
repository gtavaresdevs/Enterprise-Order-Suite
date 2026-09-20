package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.profile.domain.exception.InvalidAvatarException;
import lombok.RequiredArgsConstructor;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AvatarValidator {

  private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

  private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
    "image/jpeg",
    "image/png",
    "image/webp"
  );

  private final Tika tika = new Tika();

  public void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new InvalidAvatarException(
        "Avatar file is required"
      );
    }

    if (file.getSize() > MAX_FILE_SIZE) {
      throw new InvalidAvatarException(
        "Avatar file must not exceed 5 MB"
      );
    }

    String declaredContentType = file.getContentType();

    if (!ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
      throw new InvalidAvatarException(
        "Avatar must be a JPEG, PNG, or WebP image"
      );
    }

    try (InputStream inputStream = file.getInputStream()) {
      String detectedContentType = tika.detect(inputStream);

      // The detected type must both be allowed AND match what the client declared.
      // Checking the two independently lets a file sniffed as one allowed image type
      // through while it is stored and later served as another - the declared type is
      // what ends up on the object - so content-type spoofing passed both checks.
      if (!ALLOWED_CONTENT_TYPES.contains(detectedContentType)
        || !detectedContentType.equals(declaredContentType)) {
        throw new InvalidAvatarException(
          "Uploaded file is not a valid JPEG, PNG, or WebP image"
        );
      }
    } catch (IOException exception) {
      throw new InvalidAvatarException(
        "Unable to validate avatar file",
        exception
      );
    }
  }
}
