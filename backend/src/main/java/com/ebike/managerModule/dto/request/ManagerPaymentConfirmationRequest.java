package com.ebike.managerModule.dto.request;

public record ManagerPaymentConfirmationRequest(String providerTxnId, String note) {
}
