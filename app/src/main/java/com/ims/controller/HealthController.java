package com.ims.controller;

import com.ims.config.ImsProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class HealthController {

    private final ImsProperties props;
    private final Environment env;

    public HealthController(ImsProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        String profile = String.join(",", env.getActiveProfiles());
        return Map.of(
                "status", "UP",
                "role", props.getRole(),
                "profile", profile.isEmpty() ? "default" : profile);
    }
}
