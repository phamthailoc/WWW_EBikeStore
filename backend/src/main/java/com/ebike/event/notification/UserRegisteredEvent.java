package com.ebike.event.notification;

/** Published inside the sign-up transaction; the welcome email is sent after the commit. */
public record UserRegisteredEvent(Long userId) {
}
