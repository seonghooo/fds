package com.seongho.fds.config;

import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

// .env 파일의 환경변수를 Spring 환경에 주입
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMissing()
                    .load();

            Map<String, Object> properties = new HashMap<>();
            for (DotenvEntry entry : dotenv.entries()) {
                properties.put(entry.getKey(), entry.getValue());
            }

            if (!properties.isEmpty()) {
                environment.getPropertySources().addFirst(
                        new MapPropertySource("dotenvProperties", properties)
                );
            }
        } catch (Exception ignored) {
        }
    }
}
