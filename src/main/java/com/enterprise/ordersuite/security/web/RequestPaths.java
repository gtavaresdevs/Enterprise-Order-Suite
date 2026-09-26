package com.enterprise.ordersuite.security.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request path without the context path. getRequestURI() includes the context path
     * (SERVER_CONTEXT_PATH, /api in every real deployment) while MockMvc sends none, so a
     * filter matching the raw URI passes every test and switches off in production.
     * The URI is not normalized; StrictHttpFirewall has already rejected ';', '//', encoded
     * slashes and dot segments before any filter in the security chain runs.
     */
    public static String withinApplication(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }
}
