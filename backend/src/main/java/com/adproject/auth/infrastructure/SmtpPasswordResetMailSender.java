package com.adproject.auth.infrastructure;

import com.adproject.auth.application.PasswordResetMailSender;
import com.adproject.auth.application.PasswordResetProperties;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpPasswordResetMailSender implements PasswordResetMailSender {
    private final PasswordResetProperties properties;
    private final JavaMailSenderImpl sender;

    public SmtpPasswordResetMailSender(PasswordResetProperties properties) {
        this.properties = properties;
        this.sender = new JavaMailSenderImpl();
        sender.setHost(properties.smtpHost() == null ? "" : properties.smtpHost());
        sender.setPort(properties.smtpPort());
        sender.setUsername(properties.username());
        sender.setPassword(properties.password());
        Properties javaMail = sender.getJavaMailProperties();
        javaMail.put("mail.smtp.auth", Boolean.toString(properties.username() != null && !properties.username().isBlank()));
        javaMail.put("mail.smtp.starttls.enable", Boolean.toString(properties.startTls()));
    }

    @Override
    public boolean isConfigured() { return properties.configured(); }

    @Override
    public void sendCode(String recipient, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipient);
        message.setSubject("Your HireX password reset code");
        message.setText("Your HireX verification code is " + code + ". It expires in 15 minutes.");
        sender.send(message);
    }
}
