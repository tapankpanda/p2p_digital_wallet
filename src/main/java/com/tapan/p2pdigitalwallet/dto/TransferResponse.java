package com.tapan.p2pdigitalwallet.dto;

import java.math.BigDecimal;

public record TransferResponse(
        String referenceId,
        String fromAccountNumber,
        String toAccountNumber,
        BigDecimal amount,
        String description,
        String status
) {
}
