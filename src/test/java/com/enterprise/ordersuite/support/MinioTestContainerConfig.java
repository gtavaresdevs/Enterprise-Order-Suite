package com.enterprise.ordersuite.support;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;

@TestConfiguration(proxyBeanMethods = false)
public class MinioTestContainerConfig {

  private static final String MINIO_IMAGE = "minio/minio:latest";
  private static final String BUCKET = "eos-assets";
  private static final String REGION = "us-east-1";

  @Bean
  MinIOContainer minioContainer() {
    return new MinIOContainer(MINIO_IMAGE)
      .withUserName("testminio")
      .withPassword("testminio-password");
  }

  @Bean
  DynamicPropertyRegistrar minioProperties(
    MinIOContainer minioContainer
  ) {
    return registry -> {
      registry.add(
        "storage.endpoint",
        minioContainer::getS3URL
      );

      registry.add(
        "storage.region",
        () -> REGION
      );

      registry.add(
        "storage.access-key",
        minioContainer::getUserName
      );

      registry.add(
        "storage.secret-key",
        minioContainer::getPassword
      );

      registry.add(
        "storage.bucket",
        () -> BUCKET
      );
    };
  }

  @Bean
  SmartInitializingSingleton minioBucketInitializer(
    S3Client s3Client
  ) {
    return () -> {
      try {
        s3Client.headBucket(
          HeadBucketRequest.builder()
            .bucket(BUCKET)
            .build()
        );
      } catch (Exception exception) {
        s3Client.createBucket(
          CreateBucketRequest.builder()
            .bucket(BUCKET)
            .build()
        );
      }
    };
  }
}
