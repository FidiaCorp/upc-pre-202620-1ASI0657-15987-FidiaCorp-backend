package com.fidiacorp.credicasa.pricing.domain.model;

/** US20: efecto de un pago extraordinario (prepago) sobre el saldo remanente. */
public enum PrepaymentStrategy {
    /** Mantiene la cuota y acorta el plazo. */
    REDUCE_TERM,
    /** Mantiene el plazo y recalcula una cuota menor. */
    REDUCE_INSTALLMENT
}
