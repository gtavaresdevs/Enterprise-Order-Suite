package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.profile.domain.exception.InvalidAvatarException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class AvatarValidatorTest {

  private AvatarValidator validator;

  @BeforeEach
  void setup() {
    validator = new AvatarValidator();
  }

  @Test
  void validate_ValidPng_DoesNotThrow() throws Exception {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        createImage("png")
      );

    assertThatCode(() ->
      validator.validate(file)
    ).doesNotThrowAnyException();
  }

  @Test
  void validate_ValidJpeg_DoesNotThrow() throws Exception {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.jpg",
        "image/jpeg",
        createImage("jpg")
      );

    assertThatCode(() ->
      validator.validate(file)
    ).doesNotThrowAnyException();
  }

  @Test
  void validate_ValidWebp_DoesNotThrow() throws Exception {

    byte[] webp =
      createWebpImage();

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.webp",
        "image/webp",
        webp
      );

    assertThatCode(() ->
      validator.validate(file)
    ).doesNotThrowAnyException();
  }

  @Test
  void validate_NullFile_ThrowsInvalidAvatarException() {

    assertThatThrownBy(() ->
      validator.validate(null)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage("Avatar file is required");
  }

  @Test
  void validate_EmptyFile_ThrowsInvalidAvatarException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[0]
      );

    assertThatThrownBy(() ->
      validator.validate(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage("Avatar file is required");
  }

  @Test
  void validate_UnsupportedDeclaredContentType_ThrowsInvalidAvatarException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.gif",
        "image/gif",
        new byte[] {1, 2, 3}
      );

    assertThatThrownBy(() ->
      validator.validate(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Avatar must be a JPEG, PNG, or WebP image"
      );
  }

  @Test
  void validate_MismatchedContentType_ThrowsInvalidAvatarException()
    throws Exception {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        "this is not an image".getBytes()
      );

    assertThatThrownBy(() ->
      validator.validate(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Uploaded file is not a valid JPEG, PNG, or WebP image"
      );
  }

  @Test
  void validate_JpegContentDeclaredAsPng_ThrowsInvalidAvatarException()
    throws Exception {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        createImage("jpg")
      );

    assertThatThrownBy(() ->
      validator.validate(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Uploaded file is not a valid JPEG, PNG, or WebP image"
      );
  }

  @Test
  void validate_FileLargerThan5Mb_ThrowsInvalidAvatarException() {

    byte[] oversized =
      new byte[(5 * 1024 * 1024) + 1];

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        oversized
      );

    assertThatThrownBy(() ->
      validator.validate(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Avatar file must not exceed 5 MB"
      );
  }

  private byte[] createImage(
    String format
  ) throws Exception {

    BufferedImage image =
      new BufferedImage(
        2,
        2,
        BufferedImage.TYPE_INT_RGB
      );

    ByteArrayOutputStream output =
      new ByteArrayOutputStream();

    ImageIO.write(
      image,
      format,
      output
    );

    return output.toByteArray();
  }

  private byte[] createWebpImage() throws Exception {

    BufferedImage image =
      new BufferedImage(
        2,
        2,
        BufferedImage.TYPE_INT_RGB
      );

    var writers =
      ImageIO.getImageWritersByMIMEType("image/webp");

    if (!writers.hasNext()) {
      throw new IllegalStateException(
        "WebP ImageIO writer is not available"
      );
    }

    var writer = writers.next();

    try {
      ByteArrayOutputStream output =
        new ByteArrayOutputStream();

      try (
        var imageOutput =
          ImageIO.createImageOutputStream(output)
      ) {
        writer.setOutput(imageOutput);
        writer.write(image);
      }

      return output.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
