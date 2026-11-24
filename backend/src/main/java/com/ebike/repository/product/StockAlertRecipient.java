package com.ebike.repository.product;

/** One waiting row with just what is needed to tell its customer: the account state. */
public record StockAlertRecipient(Long alertId, Long userId, String accountStatus, Boolean active) {
}
