package com.ebike.dto.auth.request;

public record UpdateProfileRequest(
    String firstName,
    String lastName,
    String email,
    /** Required only when the email changes. */
    String currentPassword
) {
    public UpdateProfileRequest(String firstName, String lastName, String email) {
        this(firstName, lastName, email, null);
    }
}
