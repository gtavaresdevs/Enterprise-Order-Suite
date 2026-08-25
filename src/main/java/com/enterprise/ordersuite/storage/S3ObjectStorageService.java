package com.enterprise.ordersuite.storage;

import java.io.InputStream;

import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class S3ObjectStorageService implements ObjectStorageService {

  private final S3Client s3Client;
  private final ObjectStorageProperties properties;

  public S3ObjectStorageService(
    S3Client s3Client,
    ObjectStorageProperties properties
  ) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @Override
  public String upload(
    String key,
    InputStream content,
    long contentLength,
    String contentType
  ) {
    PutObjectRequest request = PutObjectRequest.builder()
      .bucket(properties.bucket())
      .key(key)
      .contentType(contentType)
      .build();

    s3Client.putObject(
      request,
      RequestBody.fromInputStream(content, contentLength)
    );

    return key;
  }

  @Override
  public void delete(String key) {
    DeleteObjectRequest request = DeleteObjectRequest.builder()
      .bucket(properties.bucket())
      .key(key)
      .build();

    s3Client.deleteObject(request);
  }

  @Override
  public String getUrl(String key) {
    return properties.endpoint()
      + "/"
      + properties.bucket()
      + "/"
      + key;
  }
}
