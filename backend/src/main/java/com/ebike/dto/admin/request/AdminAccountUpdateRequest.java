package com.ebike.dto.admin.request;

/** What an admin may change on an account's details: the name and the email (the password is never shown or set here). */
public record AdminAccountUpdateRequest(String name, String email) {
}
