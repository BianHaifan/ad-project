package com.adproject.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String smtpHost, int smtpPort, String username, String password,
                            String fromAddress, boolean startTls) {
    public boolean configured() {
        return smtpHost != null && !smtpHost.isBlank() && fromAddress != null && !fromAddress.isBlank();
    }
}
