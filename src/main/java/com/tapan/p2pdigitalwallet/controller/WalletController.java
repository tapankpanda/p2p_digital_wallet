package com.tapan.p2pdigitalwallet.controller;

import com.tapan.p2pdigitalwallet.dto.BalanceResponse;
import com.tapan.p2pdigitalwallet.dto.CreateWalletRequest;
import com.tapan.p2pdigitalwallet.dto.WalletResponse;
import com.tapan.p2pdigitalwallet.service.WalletService;
import jakarta.validation.Valid;
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

}

