package com.ses.bldizi.exception;

import lombok.Getter;

@Getter
public class PushTemporaryException extends RuntimeException {
    private final int statusCode;

    public PushTemporaryException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
