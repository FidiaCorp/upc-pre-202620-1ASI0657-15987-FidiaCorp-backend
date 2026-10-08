package com.fidiacorp.credicasa.domain.model;

import java.util.Objects;

public class GracePeriod {

    private final GracePeriodType type;
    private final int months;

    public GracePeriod(GracePeriodType type, int months) {
        this.type = type != null ? type : GracePeriodType.NONE;
        this.months = Math.max(0, months);
    }

    public static GracePeriod none() {
        return new GracePeriod(GracePeriodType.NONE, 0);
    }

    public static GracePeriod of(GracePeriodType type, int months) {
        return new GracePeriod(type, months);
    }

    public GracePeriodType getType() {
        return type;
    }

    public int getMonths() {
        return months;
    }

    public boolean isGraceActiveAt(int installmentNumber) {
        return type != GracePeriodType.NONE && installmentNumber <= months;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GracePeriod that = (GracePeriod) o;
        return months == that.months && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, months);
    }
}
