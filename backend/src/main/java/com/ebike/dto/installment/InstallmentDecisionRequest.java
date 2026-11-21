package com.ebike.dto.installment;

/** V24: the staff's verdict: {@code decision} is APPROVE or REJECT; a rejection needs a reason, an approval may carry a note. */
public record InstallmentDecisionRequest(String decision, String note) {
}
