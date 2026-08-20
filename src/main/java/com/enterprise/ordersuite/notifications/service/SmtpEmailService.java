package com.enterprise.ordersuite.notifications.service;

import com.enterprise.ordersuite.notifications.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Production-ready implementation of the EmailService interface.
 * Uses JavaMailSender to dispatch rich HTML transactional emails asynchronously via SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

  private final JavaMailSender mailSender;
  private final TemplateEngine templateEngine;
  private final EmailProperties emailProperties;

  /**
   * Sends an HTML-formatted password reset email asynchronously.
   * Executes in a separate thread pool manager to keep calling auth API response times nominal.
   *
   * @param toEmail  The recipient's target email address.
   * @param resetUrl The fully constructed frontend destination verification URL containing the token.
   */
  @Override
  @Async
  public void sendPasswordResetEmail(String toEmail, String resetUrl) {
    log.info("Initiating asynchronous password reset email dispatch sequence to: {}", toEmail);

    try {
      // Prepare the Thymeleaf rendering context variables
      Context context = new Context();
      context.setVariable("resetUrl", resetUrl);

      // Add the template variable mapping here
      context.setVariable("logoUrl", emailProperties.getLogoUrl());

      // Pass the absolute public backend base URL down to let HTML locate static assets (like /logo.png)
      context.setVariable("backendBaseUrl", emailProperties.getBackendBaseUrl());

      // Process the dynamic Thymeleaf HTML template file matching the config name
      String templateName = emailProperties.getTemplateName();
      String htmlContent = templateEngine.process(templateName, context);

      // Construct the structural low-level mime layout message properties
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

      helper.setFrom(emailProperties.getFrom());
      helper.setTo(toEmail);
      helper.setSubject("Reset Your Password - Enterprise Order Suite");
      helper.setText(htmlContent, true); // True flag dictates raw string compilation to HTML layout

      // Dispatch message onto the configured external mail broker/relay
      mailSender.send(message);
      log.info("Successfully dispatched password reset email onto exchange relay for: {}", toEmail);

    } catch (MessagingException e) {
      log.error("Failed to construct email mime structure or handshake with destination SMTP exchange for address: {}", toEmail, e);
      throw new RuntimeException("Email delivery infrastructure component error occurred", e);
    } catch (Exception e) {
      log.error("An unexpected internal runtime exception occurred during the asynchronous email compilation sequence for target: {}", toEmail, e);
      throw new RuntimeException("Unexpected notification service pipeline error occurred", e);
    }
  }
}
