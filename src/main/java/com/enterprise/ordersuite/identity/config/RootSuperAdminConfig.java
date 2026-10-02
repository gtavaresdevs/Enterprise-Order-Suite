package com.enterprise.ordersuite.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RootSuperAdminProperties.class)
public class RootSuperAdminConfig {
}
