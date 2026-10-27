package com.fidiacorp.credicasa.domain.model;
public class TwoFactorException extends RuntimeException {
    private final int status;
    private final String code;
    private final long retryAfterSeconds;
    public TwoFactorException(int status, String code, String message) { this(status, code, message, 0); }
    public TwoFactorException(int status, String code, String message, long retryAfterSeconds) {
        super(message); this.status = status; this.code = code; this.retryAfterSeconds = retryAfterSeconds;
    }
    public int status() { return status; }
    public String code() { return code; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
