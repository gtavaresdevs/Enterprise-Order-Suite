package com.enterprise.ordersuite.profile.application.service;

import com.enterprise.ordersuite.profile.domain.exception.InvalidAvatarException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

@Component
public class AvatarImageProcessor {

  private static final float WEBP_COMPRESSION_QUALITY = 0.85f;

  public byte[] convertToWebp(MultipartFile file) {
    try (InputStream inputStream = file.getInputStream()) {
      BufferedImage sourceImage = ImageIO.read(inputStream);

      if (sourceImage == null) {
        throw new InvalidAvatarException(
          "Uploaded file could not be decoded as an image"
        );
      }

      BufferedImage normalizedImage =
        normalizeImage(sourceImage);

      return encodeWebp(normalizedImage);
    } catch (IOException exception) {
      throw new InvalidAvatarException(
        "Failed to process avatar image",
        exception
      );
    }
  }

  private BufferedImage normalizeImage(
    BufferedImage sourceImage
  ) {
    BufferedImage normalizedImage =
      new BufferedImage(
        sourceImage.getWidth(),
        sourceImage.getHeight(),
        BufferedImage.TYPE_INT_ARGB
      );

    Graphics2D graphics = normalizedImage.createGraphics();

    try {
      graphics.setComposite(AlphaComposite.Src);

      graphics.drawImage(
        sourceImage,
        0,
        0,
        null
      );
    } finally {
      graphics.dispose();
    }

    return normalizedImage;
  }

  private byte[] encodeWebp(
    BufferedImage image
  ) throws IOException {

    Iterator<ImageWriter> writers =
      ImageIO.getImageWritersByMIMEType("image/webp");

    if (!writers.hasNext()) {
      throw new InvalidAvatarException(
        "WebP image writer is not available"
      );
    }

    ImageWriter writer = writers.next();

    try {
      ByteArrayOutputStream outputStream =
        new ByteArrayOutputStream();

      try (
        ImageOutputStream imageOutputStream =
          ImageIO.createImageOutputStream(outputStream)
      ) {
        writer.setOutput(imageOutputStream);

        ImageWriteParam writeParam =
          writer.getDefaultWriteParam();

        if (writeParam.canWriteCompressed()) {
          writeParam.setCompressionMode(
            ImageWriteParam.MODE_EXPLICIT
          );

          String[] compressionTypes =
            writeParam.getCompressionTypes();

          if (compressionTypes != null
            && compressionTypes.length > 0) {

            writeParam.setCompressionType(
              compressionTypes[0]
            );
          }

          writeParam.setCompressionQuality(
            WEBP_COMPRESSION_QUALITY
          );
        }

        writer.write(
          null,
          new IIOImage(image, null, null),
          writeParam
        );
      }

      return outputStream.toByteArray();
    } finally {
      writer.dispose();
    }
  }
}
