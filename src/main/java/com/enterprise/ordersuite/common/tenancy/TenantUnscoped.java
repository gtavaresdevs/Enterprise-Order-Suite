package com.enterprise.ordersuite.common.tenancy;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Exempts one repository method of a restaurant-owned entity from the tenant rule
// (TenantRepositoryRuleTest). Every use states why the query cannot take a restaurant id.
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface TenantUnscoped {

    String value();
}
