package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.profile.domain.exception.InvalidAvatarException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;

import javax.imageio.ImageWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AvatarImageProcessorTest {

  private AvatarImageProcessor processor;

  @BeforeEach
  void setup() {
    processor = new AvatarImageProcessor();
  }

  @Test
  void convertToWebp_PngImage_ReturnsValidWebpImage()
    throws Exception {

    byte[] png =
      createImage("png");

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        png
      );

    byte[] result =
      processor.convertToWebp(file);

    assertThat(result)
      .isNotEmpty();

    try (
      ByteArrayInputStream input =
        new ByteArrayInputStream(result)
    ) {
      BufferedImage decoded =
        ImageIO.read(input);

      assertThat(decoded)
        .isNotNull();

      assertThat(decoded.getWidth())
        .isEqualTo(2);

      assertThat(decoded.getHeight())
        .isEqualTo(2);
    }

    assertThat(ImageIO.getImageReadersByMIMEType("image/webp")
      .hasNext())
      .isTrue();
  }

  @Test
  void convertToWebp_JpegImage_ReturnsValidImage()
    throws Exception {

    byte[] jpeg =
      createImage("jpg");

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.jpg",
        "image/jpeg",
        jpeg
      );

    byte[] result =
      processor.convertToWebp(file);

    assertThat(result)
      .isNotEmpty();

    BufferedImage decoded =
      ImageIO.read(
        new ByteArrayInputStream(result)
      );

    assertThat(decoded)
      .isNotNull();

    assertThat(decoded.getWidth())
      .isEqualTo(2);

    assertThat(decoded.getHeight())
      .isEqualTo(2);
  }

  @Test
  void convertToWebp_InvalidImage_ThrowsInvalidAvatarException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        "not an image".getBytes()
      );

    assertThatThrownBy(() ->
      processor.convertToWebp(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Uploaded file could not be decoded as an image"
      );
  }

  @Test
  void convertToWebp_EmptyImage_ThrowsInvalidAvatarException() {

    MultipartFile file =
      new MockMultipartFile(
        "file",
        "avatar.png",
        "image/png",
        new byte[0]
      );

    assertThatThrownBy(() ->
      processor.convertToWebp(file)
    )
      .isInstanceOf(InvalidAvatarException.class)
      .hasMessage(
        "Uploaded file could not be decoded as an image"
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

    image.setRGB(0, 0, 0xFF0000);
    image.setRGB(1, 0, 0x00FF00);
    image.setRGB(0, 1, 0x0000FF);
    image.setRGB(1, 1, 0xFFFFFF);

    ByteArrayOutputStream output =
      new ByteArrayOutputStream();

    ImageIO.write(
      image,
      format,
      output
    );

    return output.toByteArray();
  }
}
