package com.tapan.p2pdigitalwallet.dto;


import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TransferRequest (

        @NotBlank(message = "Source account number is required")
        @Pattern(regexp = TransferRequest.ACCOUNT_NUMBER_REGEX,
                message = TransferRequest.ACCOUNT_NUMBER_MESSAGE)
        String fromAccountNumber,

        @NotBlank(message = "Destination account number is required")
        @Pattern(regexp = TransferRequest.ACCOUNT_NUMBER_REGEX,
                message = TransferRequest.ACCOUNT_NUMBER_MESSAGE)
        String toAccountNumber,

        @NotNull(message = "Transfer Amount is required")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 2,
                message = "Amount must have at most 15 integer digits and 2 decimal places")
        BigDecimal amount,

        @Size(max = 250, message = "Description must be at most 250 characters")
        String description
){
    public static final String ACCOUNT_NUMBER_REGEX = "^[1-9][0-9]{11}$";
    public static final String ACCOUNT_NUMBER_MESSAGE =
            "Account number must be exactly 12 digits and must not start with 0";
}
