package com.adproject;

import com.adproject.auth.application.AuthProperties;
import com.adproject.auth.application.MailProperties;
import com.adproject.agent.application.AgentProperties;
import com.adproject.agent.application.DeepSeekProperties;
import com.adproject.integration.google.application.GoogleOAuthProperties;
import com.adproject.recommendation.application.RecommendationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AuthProperties.class, MailProperties.class, AgentProperties.class,
        DeepSeekProperties.class, GoogleOAuthProperties.class, RecommendationProperties.class})
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
