package com.adproject;

import com.adproject.auth.application.AuthProperties;
import com.adproject.auth.application.PasswordResetProperties;
import com.adproject.integration.google.application.GoogleOAuthProperties;
import com.adproject.recommendation.application.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AuthProperties.class, PasswordResetProperties.class, GoogleOAuthProperties.class, RecommendationProperties.class})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
