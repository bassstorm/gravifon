package com.gravifon.player.streaming;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RangeNotSatisfiableException extends ResponseStatusException {
    private final HttpHeaders headers;

    public RangeNotSatisfiableException(long fileLength) {
        super(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "Range not satisfiable. Expected bytes within resource length %d".formatted(fileLength)
        );
        this.headers = new HttpHeaders();
        this.headers.set(HttpHeaders.CONTENT_RANGE, "bytes */%d".formatted(fileLength));
    }

    @Override
    public HttpHeaders getResponseHeaders() {
        return headers;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }
}
