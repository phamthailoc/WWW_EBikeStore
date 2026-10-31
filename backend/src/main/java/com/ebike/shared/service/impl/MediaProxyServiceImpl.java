package com.ebike.shared.service.impl;

import com.ebike.shared.service.MediaProxyService;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MediaProxyServiceImpl implements MediaProxyService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(6);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(CONNECT_TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    @Override
    public ResponseEntity<byte[]> proxyImage(String url) {
        URI remoteUri = parseAndValidate(url);

        try {
            HttpRequest request = HttpRequest.newBuilder(remoteUri)
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 EBikeMediaProxy/1.0")
                .GET()
                .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to fetch remote image");
            }

            return ResponseEntity.ok()
                .contentType(resolveMediaType(response, remoteUri))
                .cacheControl(CacheControl.noCache())
                .body(response.body());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote image is temporarily unavailable", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Remote image is temporarily unavailable", exception);
        }
    }

    private MediaType resolveMediaType(HttpResponse<byte[]> response, URI remoteUri) {
        String contentType = response.headers()
            .firstValue(HttpHeaders.CONTENT_TYPE)
            .orElseGet(() -> URLConnection.guessContentTypeFromName(remoteUri.getPath()));

        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private URI parseAndValidate(String rawUrl) {
        try {
            URI uri = new URI(rawUrl);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image URL scheme");
            }
            if (host == null || host.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image URL host");
            }
            if (host.equalsIgnoreCase("localhost") || host.startsWith("127.") || host.startsWith("192.168.") || host.startsWith("10.")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocked image host");
            }

            return uri;
        } catch (URISyntaxException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed image URL", exception);
        }
    }
}
