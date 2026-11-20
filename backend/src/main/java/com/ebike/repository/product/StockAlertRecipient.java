package com.ebike.repository.product;

/** One waiting row with just what is needed to tell its customer: the account state and the language chosen in the profile. */
public record StockAlertRecipient(Long alertId, Long userId, String accountStatus, Boolean active, String language) {
}
