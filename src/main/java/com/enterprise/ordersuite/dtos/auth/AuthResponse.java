    package com.enterprise.ordersuite.dtos.auth;

    import lombok.AllArgsConstructor;
    import lombok.Getter;
    import lombok.Setter;

    @Getter
    @Setter
    @AllArgsConstructor
    public class AuthResponse {
        private String accessToken;
    }