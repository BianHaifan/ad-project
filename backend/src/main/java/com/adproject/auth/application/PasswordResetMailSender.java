package com.adproject.auth.application;

public interface PasswordResetMailSender {
    boolean isConfigured();
    void sendCode(String recipient, String code);
}
