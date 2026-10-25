package com.ses.bldizi.exception;

public class PushSubscriptionExpiredException extends RuntimeException {
    public PushSubscriptionExpiredException(String message) {
        super(message);
    }
}
