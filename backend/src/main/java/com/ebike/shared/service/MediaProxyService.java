package com.ebike.shared.service;

import org.springframework.http.ResponseEntity;

public interface MediaProxyService {

    ResponseEntity<byte[]> proxyImage(String url);
}
