package com.ebike.dto.manager.request;

public record ManagerPaymentConfirmationRequest(String providerTxnId, String note) {
}
