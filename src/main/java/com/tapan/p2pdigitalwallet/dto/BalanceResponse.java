package com.tapan.p2pdigitalwallet.dto;

import com.tapan.p2pdigitalwallet.entity.Wallet;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        BigDecimal balance
) {
    public static BalanceResponse from(Wallet wallet){
        return new BalanceResponse(
                wallet.getAccountNumber(),
                wallet.getBalance()
        );
    }
}
