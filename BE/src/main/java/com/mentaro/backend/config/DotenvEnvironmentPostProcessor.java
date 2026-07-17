package com.mentaro.backend.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

// Loads BE/.env into the Spring Environment for local development, since it's
// plain KEY=VALUE lines (valid java.util.Properties syntax). Not needed in
// production: Railway injects real env vars directly, and .env is gitignored
// so it never ships in the image.
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path envFile = Path.of(".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }

        Properties properties = new Properties();
        try (var in = Files.newInputStream(envFile)) {
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + envFile.toAbsolutePath(), e);
        }

        Map<String, Object> values = new LinkedHashMap<>();
        properties.forEach((key, value) -> values.put((String) key, value));

        environment.getPropertySources().addAfter("systemEnvironment", new MapPropertySource("dotenv", values));
    }
}
