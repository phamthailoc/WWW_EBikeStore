package com.ebike.controller.common;

import com.ebike.service.media.MediaProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
public class MediaProxyController {

    private final MediaProxyService mediaProxyService;

    public MediaProxyController(MediaProxyService mediaProxyService) {
        this.mediaProxyService = mediaProxyService;
    }

    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam String url) {
        return mediaProxyService.proxyImage(url);
    }
}
