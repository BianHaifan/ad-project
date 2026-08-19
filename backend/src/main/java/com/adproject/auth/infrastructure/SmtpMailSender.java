package com.adproject.auth.infrastructure;

import com.adproject.auth.application.MailProperties;
import com.adproject.auth.application.MailSender;
import java.util.Properties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpMailSender implements MailSender {
    private final MailProperties properties;
    private final JavaMailSenderImpl sender;

    public SmtpMailSender(MailProperties properties) {
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
    public void send(String recipient, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.fromAddress());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(text);
        sender.send(message);
    }
}
