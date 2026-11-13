package com.ebike.controller.common;

import java.sql.Connection;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @Value("${spring.application.name:ebike-backend}")
    private String applicationName;

    @Value("${app.instance-id:${HOSTNAME:local}}")
    private String instanceId;

    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("UP", applicationName, instanceId, OffsetDateTime.now().toString(), Map.of());
    }

    @GetMapping("/live")
    public HealthResponse liveness() {
        return new HealthResponse("UP", applicationName, instanceId, OffsetDateTime.now().toString(), Map.of());
    }

    @GetMapping("/ready")
    public ResponseEntity<HealthResponse> readiness() {
        Map<String, String> dependencies = new LinkedHashMap<>();
        dependencies.put("database", checkDatabase());

        boolean ready = dependencies.values().stream().allMatch("UP"::equals);
        HealthResponse response = new HealthResponse(
            ready ? "UP" : "DOWN",
            applicationName,
            instanceId,
            OffsetDateTime.now().toString(),
            dependencies
        );

        return ResponseEntity
            .status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
            .body(response);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return "UP";
        } catch (Exception exception) {
            return "DOWN";
        }
    }

    public record HealthResponse(
        String status,
        String application,
        String instanceId,
        String timestamp,
        Map<String, String> dependencies
    ) {
    }
}
