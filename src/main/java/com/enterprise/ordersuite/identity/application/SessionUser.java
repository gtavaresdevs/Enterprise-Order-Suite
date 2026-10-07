package com.enterprise.ordersuite.identity.application;

// What the app shell shows from the first paint (header, avatar, restaurant name), returned
// by login and refresh next to the access token so the frontend needs no GET /me first
// (Tenancy & Identity D-20). Display data only; it never goes into the token.
// restaurantId and restaurantName are null for the platform admin.
public record SessionUser(
        String id,
        String firstName,
        String lastName,
        String email,
        String avatarUrl,
        String restaurantId,
        String restaurantName
) {}
