package com.adproject.auth.application;

public interface MailSender {
    boolean isConfigured();
    void send(String recipient, String subject, String text);
}
