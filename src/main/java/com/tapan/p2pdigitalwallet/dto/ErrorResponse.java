package com.tapan.p2pdigitalwallet.dto;

import java.time.Instant;

public record ErrorResponse(

        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
}
