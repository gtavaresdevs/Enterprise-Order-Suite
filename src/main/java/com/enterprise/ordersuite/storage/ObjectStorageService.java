package com.enterprise.ordersuite.storage;

import java.io.InputStream;

public interface ObjectStorageService {

  String upload(
    String key,
    InputStream content,
    long contentLength,
    String contentType
  );

  void delete(String key);

  String getUrl(String key);
}
