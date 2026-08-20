package com.enterprise.ordersuite.notifications.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Type-safe configuration binder bean mapping structural properties
 * from application.yml starting under the 'app.email' namespace prefix hierarchy.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {

  /**
   * The 'From' destination envelope sender mailbox identity address utilized
   * globally across platform transactional mail templates (e.g., gtavaresdev@gmail.com).
   */
  private String from;

  /**
   * The classpath file name reference pointing towards the dynamic rendering
   * Thymeleaf resource layout (defaults to "password-reset-email").
   */
  private String templateName = "password-reset-email";

  /**
   * The absolute, fully qualified public root base URL of the active backend service instance.
   * Crucial for compiling cross-origin fully distinct paths for static embedded resources (like logo images).
   * e.g., http://localhost:8080 or https://api.yourproductionsite.com
   */
  private String backendBaseUrl;
  private String logoUrl;
}
