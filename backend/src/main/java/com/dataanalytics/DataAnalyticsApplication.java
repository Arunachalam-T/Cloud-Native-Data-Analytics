package com.dataanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@EnableAsync
public class DataAnalyticsApplication {

    public static void main(String[] args) {
        loadEnvIfPresent();
        SpringApplication.run(DataAnalyticsApplication.class, args);
    }

    private static void loadEnvIfPresent() {
        Path[] possiblePaths = {Path.of(".env"), Path.of("../.env"), Path.of("../../.env")};
        for (Path path : possiblePaths) {
            if (Files.exists(path)) {
                try (var lines = Files.lines(path)) {
                    lines.filter(l -> !l.isBlank() && !l.trim().startsWith("#") && l.contains("="))
                         .forEach(line -> {
                             int idx = line.indexOf('=');
                             String key = line.substring(0, idx).trim();
                             String val = line.substring(idx + 1).trim();
                             if (System.getProperty(key) == null && System.getenv(key) == null) {
                                 System.setProperty(key, val);
                             }
                         });
                    break;
                } catch (Exception ignored) {}
            }
        }
    }
}
