package com.enterprise.ordersuite.support;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.net.URI;

@TestConfiguration(proxyBeanMethods = false)
public class MinioTestContainerConfig {

  // minio/minio is no longer published on Docker Hub, so a clean machine (CI, cloud sessions)
  // cannot pull it. pgsty/minio is a maintained build of the same server; pinned for
  // reproducible runs.
  private static final DockerImageName MINIO_IMAGE = DockerImageName
    .parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
    .asCompatibleSubstituteFor("minio/minio");
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
