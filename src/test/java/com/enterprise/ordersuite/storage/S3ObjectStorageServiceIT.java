package com.enterprise.ordersuite.storage;

import com.enterprise.ordersuite.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class S3ObjectStorageServiceIT {

  @Autowired
  private ObjectStorageService objectStorageService;

  @Autowired
  private ObjectStorageProperties properties;

  @Autowired
  private S3Client s3Client;

  @Test
  void upload_ValidObject_StoresObjectInMinio() {

    String key =
      "test/avatar-" + UUID.randomUUID() + ".txt";

    byte[] content =
      "Enterprise Order Suite storage test"
        .getBytes(StandardCharsets.UTF_8);

    try {
      String returnedKey =
        objectStorageService.upload(
          key,
          new ByteArrayInputStream(content),
          content.length,
          "text/plain"
        );

      assertThat(returnedKey)
        .isEqualTo(key);

      var head =
        s3Client.headObject(
          HeadObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .build()
        );

      assertThat(head.contentLength())
        .isEqualTo(content.length);

      assertThat(head.contentType())
        .isEqualTo("text/plain");

    } finally {
      objectStorageService.delete(key);
    }
  }

  @Test
  void delete_ExistingObject_RemovesObjectFromMinio() {

    String key =
      "test/delete-" + UUID.randomUUID() + ".txt";

    byte[] content =
      "Object to delete"
        .getBytes(StandardCharsets.UTF_8);

    objectStorageService.upload(
      key,
      new ByteArrayInputStream(content),
      content.length,
      "text/plain"
    );

    var existingObject =
      s3Client.headObject(
        HeadObjectRequest.builder()
          .bucket(properties.bucket())
          .key(key)
          .build()
      );

    assertThat(existingObject)
      .isNotNull();

    objectStorageService.delete(key);

    assertThatThrownBy(() ->
      s3Client.headObject(
        HeadObjectRequest.builder()
          .bucket(properties.bucket())
          .key(key)
          .build()
      )
    )
      .isInstanceOf(SdkClientException.class);
  }

  @Test
  void getUrl_ValidKey_ReturnsExpectedUrl() {

    String key =
      "avatars/test-avatar.webp";

    String url =
      objectStorageService.getUrl(key);

    assertThat(url)
      .isEqualTo(
        properties.endpoint()
          + "/"
          + properties.bucket()
          + "/"
          + key
      );
  }
}
