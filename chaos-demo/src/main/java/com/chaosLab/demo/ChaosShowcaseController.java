package com.chaosLab.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/demo/showcase")
public class ChaosShowcaseController {

    private final ChaosShowcaseService showcaseService;

    public ChaosShowcaseController(ChaosShowcaseService showcaseService) {
        this.showcaseService = showcaseService;
    }

    @GetMapping
    public Map<String, Object> info(HttpServletRequest request) {
        String baseUrl = resolveBaseUrl(request);
        return Map.of(
                "message", "Trigger a full resilience demo run against this Spring Boot app.",
                "runEndpoint", "POST /demo/showcase/run?mode=quick|full",
                "defaultMode", "quick",
                "baseUrlUsedByDefault", baseUrl,
                "notes", "The run executes baseline -> fault -> recovery, then returns PASS/FAIL with phase metrics and artifact paths."
        );
    }

    @PostMapping("/run")
    public ChaosShowcaseService.ShowcaseRunResult run(
            @RequestParam(defaultValue = "quick") String mode,
            @RequestParam(required = false) String baseUrl,
            HttpServletRequest request
    ) {
        ShowcaseMode showcaseMode = ShowcaseMode.parse(mode);
        String resolvedBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? resolveBaseUrl(request)
                : baseUrl;
        try {
            return showcaseService.run(resolvedBaseUrl, showcaseMode);
        } catch (IllegalStateException e) {
            if ("Showcase run is already in progress".equals(e.getMessage())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
            }
            throw e;
        }
    }

    private static String resolveBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        return scheme + "://" + host + ":" + port;
    }
}
