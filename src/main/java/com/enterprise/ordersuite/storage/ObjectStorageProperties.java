package com.enterprise.ordersuite.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage")
public record ObjectStorageProperties(
  String endpoint,
  String region,
  String accessKey,
  String secretKey,
  String bucket
) {
}
