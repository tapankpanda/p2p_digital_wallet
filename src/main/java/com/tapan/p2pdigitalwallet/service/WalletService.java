package com.tapan.p2pdigitalwallet.service;

import com.tapan.p2pdigitalwallet.dto.BalanceResponse;
import com.tapan.p2pdigitalwallet.dto.CreateWalletRequest;
import com.tapan.p2pdigitalwallet.dto.WalletResponse;
import com.tapan.p2pdigitalwallet.entity.User;
import com.tapan.p2pdigitalwallet.entity.Wallet;
import com.tapan.p2pdigitalwallet.repository.UserRepository;
import com.tapan.p2pdigitalwallet.repository.WalletRepository;
import com.tapan.p2pdigitalwallet.util.AccountNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class WalletService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AccountNumberGenerator accountNumberGenerator;

    public WalletService(UserRepository userRepository, WalletRepository walletRepository, AccountNumberGenerator accountNumberGenerator) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.accountNumberGenerator = accountNumberGenerator;
    }


    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request){
        String email = User.normalizeEmail(request.email());
        if(userRepository.existsByEmail(email)){
            throw new RuntimeException("A user with this email already has a wallet.");
        }
        User user = userRepository.save(new User(request.fullName().trim(), email));

        String accountNumber = accountNumberGenerator.generate();
        while(walletRepository.existsByAccountNumber(accountNumber)){
            accountNumber = accountNumberGenerator.generate();
        }

        Wallet wallet = walletRepository.save(new Wallet(user, accountNumber, request.initialBalance()));
        return WalletResponse.from(wallet);

    }


    @Transactional(readOnly = true)
    public WalletResponse getWallet(String accountNumber){
        Wallet wallet =  walletRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return WalletResponse.from(wallet);
    }


    @Transactional (readOnly = true)
    public BalanceResponse getBalance(String accountNumber){
        Wallet wallet = walletRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return BalanceResponse.from(wallet);
    }


}
