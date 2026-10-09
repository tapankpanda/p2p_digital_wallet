package com.tapan.p2pdigitalwallet.controller;

import com.tapan.p2pdigitalwallet.dto.BalanceResponse;
import com.tapan.p2pdigitalwallet.dto.CreateWalletRequest;
import com.tapan.p2pdigitalwallet.dto.TransactionResponse;
import com.tapan.p2pdigitalwallet.dto.WalletResponse;
import com.tapan.p2pdigitalwallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private  final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }


    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request){
        return  ResponseEntity.status(HttpStatus.CREATED).body(walletService.createWallet(request));
    }

    @GetMapping("/{accountNumber}")
    public WalletResponse getWallet(@PathVariable String accountNumber){
        validateAccountNumber(accountNumber);
        return walletService.getWallet(accountNumber);
    }

    @GetMapping("/{accountNumber}/balance")
    public BalanceResponse getBalance(@PathVariable String accountNumber){
        validateAccountNumber(accountNumber);
        return walletService.getBalance(accountNumber);
    }


    private void validateAccountNumber(String accountNumber) {
        if (accountNumber == null || !accountNumber.matches("^[1-9][0-9]{11}$")) {
            throw new IllegalArgumentException("Account number must be exactly 12 digits and cannot start with 0.");
        }
    }

    @GetMapping("/{accountNumber}/transactions")
    public Page<TransactionResponse> getTransactions(
            @PathVariable String accountNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        validateAccountNumber(accountNumber);

        if (page < 0) {
            throw new IllegalArgumentException("Page number cannot be negative");
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100");
        }

        Pageable pageable = PageRequest.of(page, size);

        return walletService.getTransactions(accountNumber, pageable);
    }

}

