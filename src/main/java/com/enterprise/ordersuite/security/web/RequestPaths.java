package com.enterprise.ordersuite.security.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The decoded request path without the context path. getRequestURI() includes the context
     * path (SERVER_CONTEXT_PATH, /api in every real deployment) while MockMvc sends none, so a
     * filter matching the raw URI passes every test and switches off in production.
     * The path is percent-decoded because Spring MVC routes on decoded segments: matching the raw
     * URI let /auth/%6cogin reach the login endpoint while every filter skipped it.
     * StrictHttpFirewall has already rejected ';', '//', encoded slashes, encoded '%' and dot
     * segments before any filter in the security chain runs, so decoding cannot produce them.
     */
    public static String withinApplication(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }
}
