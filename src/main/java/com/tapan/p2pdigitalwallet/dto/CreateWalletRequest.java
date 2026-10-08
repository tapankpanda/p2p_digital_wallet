package com.tapan.p2pdigitalwallet.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;


public record CreateWalletRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 to 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotNull(message = "Initial balance is required")
        @DecimalMin(value = "0.00", message = "Initial balance must not be negative")
        @Digits(integer = 15, fraction = 2, message = "Initial balance must have at most 15 integer digits and 2 decimal places.")
        BigDecimal initialBalance

) {
}
