package com.ses.bldizi.exception;

import lombok.Getter;

@Getter
public class PushPermanentException extends RuntimeException {
    private final int statusCode;

    public PushPermanentException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
