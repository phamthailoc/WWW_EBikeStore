package com.ebike.service.media;

import org.springframework.http.ResponseEntity;

public interface MediaProxyService {

    ResponseEntity<byte[]> proxyImage(String url);
}
