package com.tapan.p2pdigitalwallet.dto;

import com.tapan.p2pdigitalwallet.entity.Wallet;
import com.tapan.p2pdigitalwallet.entity.WalletStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(
        String accountNumber,
        String fullName,
        String email,
        BigDecimal balance,
        WalletStatus status,
        Instant createdAt,
        Instant updatedAt

) {
    public static WalletResponse from (Wallet wallet){
        return new WalletResponse(
                wallet.getAccountNumber(),
                wallet.getUser().getFullName(),
                wallet.getUser().getEmail(),
                wallet.getBalance(),
                wallet.getStatus(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt()
        );
    }

}
