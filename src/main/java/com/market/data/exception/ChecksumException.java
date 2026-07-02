package com.market.data.exception;

public class ChecksumException extends MarketDataException {
    public ChecksumException(String message) {
        super(message);
    }

    public ChecksumException(String message, Throwable cause) {
        super(message, cause);
    }
}
