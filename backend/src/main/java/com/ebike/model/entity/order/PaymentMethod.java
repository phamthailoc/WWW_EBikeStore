package com.ebike.model.entity.order;

public enum PaymentMethod {
    PAY_LATER,
    VNPAY,
    /** V24: the payment row holds the down payment; the rest is the simulated instalment plan of the order's installment application. */
    INSTALLMENT
}
