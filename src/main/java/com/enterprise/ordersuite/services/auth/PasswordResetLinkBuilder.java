package com.enterprise.ordersuite.services.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PasswordResetLinkBuilder {

    private final String baseUrl;

    public PasswordResetLinkBuilder(@Value("${app.urls.password-reset}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String build(String rawToken) {
        // If later your baseUrl becomes a frontend page, this will still work.
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("token", rawToken)
                .build()
                .toUriString();
    }
}
