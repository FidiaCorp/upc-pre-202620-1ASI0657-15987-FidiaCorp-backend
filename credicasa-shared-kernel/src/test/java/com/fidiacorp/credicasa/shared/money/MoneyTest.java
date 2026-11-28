package com.fidiacorp.credicasa.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {
    @Test
    void normalizesToCentsWithBankersRounding() {
        assertThat(Money.of("10.005", Currency.PEN).amount()).isEqualByComparingTo("10.00");
        assertThat(Money.of("10.015", Currency.PEN).toPlainString()).isEqualTo("10.02");
        assertThat(Money.zero(Currency.USD).toPlainString()).isEqualTo("0.00");
    }

    @Test
    void arithmeticAndComparisonInSameCurrency() {
        Money a = Money.of("100.10", Currency.PEN);
        Money b = Money.of("0.90", Currency.PEN);
        assertThat(a.plus(b)).isEqualTo(Money.of("101.00", Currency.PEN));
        assertThat(b.minus(a).isNegative()).isTrue();
        assertThat(a.minus(b).isPositive()).isTrue();
        assertThat(Money.zero(Currency.PEN).isPositive()).isFalse();
        assertThat(Money.zero(Currency.PEN).isNegative()).isFalse();
        assertThat(a.times(new BigDecimal("0.5"))).isEqualTo(Money.of("50.05", Currency.PEN));
        assertThat(a.compareTo(b)).isPositive();
    }

    @Test
    void rejectsMixedCurrenciesAndNulls() {
        Money pen = Money.of("1", Currency.PEN);
        Money usd = Money.of("1", Currency.USD);
        assertThatThrownBy(() -> pen.plus(usd)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PEN");
        assertThatThrownBy(() -> pen.compareTo(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> pen.times(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(null, Currency.PEN)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null)).isInstanceOf(NullPointerException.class);
    }
}
