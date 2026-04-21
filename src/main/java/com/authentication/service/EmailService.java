package com.authentication.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verificationLink = frontendUrl + "/auth/verify-email?token=" + token;
        String subject = "Authentication Project – Verify Your Email";
        String body = buildVerificationEmailBody(fullName, verificationLink);
        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String fullName, String token) {
        String resetLink = frontendUrl + "/auth/reset-password?token=" + token;
        String subject = "Authentication Project – Reset Your Password";
        String body = buildPasswordResetEmailBody(fullName, resetLink);
        sendHtmlEmail(toEmail, subject, body);
    }

    

    @Async
    public void sendPasswordResetSuccessEmail(String toEmail, String fullName) {
        String subject = "Authentication Project – Password Changed Successfully";
        String body = buildPasswordResetSuccessEmailBody(fullName);
        sendHtmlEmail(toEmail, subject, body);
    }

    @Async
    public void sendWelcomeEmail(String toEmail, String fullName) {
        String subject = "Welcome to Authentication Project!";
        String body = buildWelcomeEmailBody(fullName);
        sendHtmlEmail(toEmail, subject, body);
    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, "Authentication Project");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildVerificationEmailBody(String name, String link) {
        return """
            <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <div style="background: #1a1a2e; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                <h1 style="color: #00d4ff; margin: 0;">Authentication Project</h1>
              </div>
              <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 8px 8px;">
                <h2>Hi %s,</h2>
                <p>Thanks for joining Authentication Project! Please verify your email to get started.</p>
                <div style="text-align: center; margin: 30px 0;">
                  <a href="%s" style="background: #00d4ff; color: white; padding: 14px 32px;
                     text-decoration: none; border-radius: 6px; font-weight: bold;">
                     Verify Email
                  </a>
                </div>
                <p style="color: #888; font-size: 12px;">This link expires in 24 hours.</p>
              </div>
            </body></html>
            """.formatted(name, link);
    }

    private String buildPasswordResetEmailBody(String name, String link) {
        return """
            <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <div style="background: #1a1a2e; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                <h1 style="color: #00d4ff; margin: 0;">Authentication Project</h1>
              </div>
              <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 8px 8px;">
                <h2>Hi %s,</h2>
                <p>We received a request to reset your password. Click below to proceed.</p>
                <div style="text-align: center; margin: 30px 0;">
                  <a href="%s" style="background: #e74c3c; color: white; padding: 14px 32px;
                     text-decoration: none; border-radius: 6px; font-weight: bold;">
                     Reset Password
                  </a>
                </div>
                <p style="color: #888; font-size: 12px;">Link expires in 1 hour. Ignore if you didn't request this.</p>
              </div>
            </body></html>
            """.formatted(name, link);
    }


    
    private String buildPasswordResetSuccessEmailBody(String name) {
        return """
            <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <div style="background: #1a1a2e; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                <h1 style="color: #00d4ff; margin: 0;">Authentication Project</h1>
              </div>
              <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 8px 8px;">
                <h2>Hi %s,</h2>
                <p>Your Authentication Project password has been <strong>successfully changed</strong>.</p>
                <p>You can now log in with your new password.</p>
                <div style="text-align: center; margin: 30px 0;">
                  <a href="%s/auth/login" style="background: #00c896; color: #080f1a; padding: 14px 32px;
                     text-decoration: none; border-radius: 6px; font-weight: bold;">
                     Sign In
                  </a>
                </div>
                <p style="color: #888; font-size: 12px;">
                  If you did not make this change, please contact support immediately.
                </p>
              </div>
            </body></html>
            """.formatted(name, frontendUrl);
    }

    private String buildWelcomeEmailBody(String name) {
        return """
            <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
              <div style="background: #1a1a2e; padding: 30px; text-align: center; border-radius: 8px 8px 0 0;">
                <h1 style="color: #00d4ff; margin: 0;">Authentication Project</h1>
              </div>
              <div style="background: #f9f9f9; padding: 30px; border-radius: 0 0 8px 8px;">
                <h2>Welcome, %s! 🎉</h2>
                <p>Your account is now active. Start exploring jobs, taking skill assessments, and building your career!</p>
                <div style="text-align: center; margin: 30px 0;">
                  <a href="%s/dashboard" style="background: #00d4ff; color: white; padding: 14px 32px;
                     text-decoration: none; border-radius: 6px; font-weight: bold;">
                     Go to Dashboard
                  </a>
                </div>
              </div>
            </body></html>
            """.formatted(name, frontendUrl);
    }
}