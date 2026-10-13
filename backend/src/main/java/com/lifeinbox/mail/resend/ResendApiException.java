package com.lifeinbox.mail.resend;

public class ResendApiException extends RuntimeException {

    private final int statusCode;

    public ResendApiException(String message) {
        super(message);
        this.statusCode = 0;
    }

    public ResendApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public ResendApiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
