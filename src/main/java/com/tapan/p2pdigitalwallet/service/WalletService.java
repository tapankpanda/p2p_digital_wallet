package com.tapan.p2pdigitalwallet.service;

import com.tapan.p2pdigitalwallet.dto.*;
import com.tapan.p2pdigitalwallet.entity.*;
import com.tapan.p2pdigitalwallet.repository.TransactionRepository;
import com.tapan.p2pdigitalwallet.repository.UserRepository;
import com.tapan.p2pdigitalwallet.repository.WalletRepository;
import com.tapan.p2pdigitalwallet.util.AccountNumberGenerator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;


@Service
public class WalletService {

    private static final BigDecimal MAX_TRANSFER_AMOUNT =
            new BigDecimal("999999999999999.99");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final TransactionRepository transactionRepository;

    public WalletService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            AccountNumberGenerator accountNumberGenerator,
            TransactionRepository transactionRepository
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.transactionRepository = transactionRepository;
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

        Wallet wallet = walletRepository
                .save(new Wallet(user, accountNumber, request.initialBalance()));

        // Record initial funding only when the amount is positive
        if(request.initialBalance().signum() > 0){
            Transaction transaction = new Transaction(
                    UUID.randomUUID().toString(),
                    wallet,
                    TransactionType.CREDIT,
                    request.initialBalance(),
                    BigDecimal.ZERO,
                    request.initialBalance(),
                    "Initial wallet funding"
            );
            transactionRepository.save(transaction);
        }

        return WalletResponse.from(wallet);

    }

    // Retrieve wallet details
    @Transactional(readOnly = true)
    public WalletResponse getWallet(String accountNumber){
        Wallet wallet =  walletRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return WalletResponse.from(wallet);
    }

    // Retrieve wallet balance
    @Transactional (readOnly = true)
    public BalanceResponse getBalance(String accountNumber){
        Wallet wallet = walletRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));
        return BalanceResponse.from(wallet);
    }

    // Retrieve paginated transaction history
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(
            String accountNumber,
            Pageable pageable
    ) {
        Wallet wallet = walletRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Wallet not found"));

        return transactionRepository
                .findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), pageable)
                .map(TransactionResponse::from);
    }

    // Transfer money between two wallets
    @Transactional
    public TransferResponse transfer(TransferRequest request){

        BigDecimal amount = request.amount();

        if(amount == null || amount.signum() <= 0){
            throw new IllegalArgumentException("Transfer amount must be greater than zero");
        }

        if (amount.compareTo(MAX_TRANSFER_AMOUNT) > 0
                || amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount must have at most 15 integer digits and 2 decimal places"
            );
        }

        if(request.fromAccountNumber()
                .equals(request.toAccountNumber())){
            throw new IllegalArgumentException("Source and destination must be different");
        }

        // Get only the IDs before locking either wallet
        Long senderId = walletRepository
                .findIdByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        Long receiverId = walletRepository
                .findIdByAccountNumber(request.toAccountNumber())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        Wallet firstLocked;
        Wallet secondLocked;

        // Lock wallets in ascending ID order to reduce deadlock risk
        if(senderId <receiverId){
            firstLocked = walletRepository
                    .findByIdForUpdate(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

            secondLocked = walletRepository
                    .findByIdForUpdate(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));
        }
        else{
            firstLocked = walletRepository
                    .findByIdForUpdate(receiverId)
                    .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

            secondLocked = walletRepository
                    .findByIdForUpdate(senderId)
                    .orElseThrow(() -> new RuntimeException("Sender wallet not found"));
        }

        // Map the locked wallets back to sender and receiver
        Wallet sender = firstLocked.getId()
                .equals(senderId) ? firstLocked : secondLocked;

        Wallet receiver = firstLocked.getId()
                .equals(receiverId) ? firstLocked : secondLocked;

        if(sender.getStatus() != WalletStatus.ACTIVE){
            throw new IllegalArgumentException("Sender wallet is not active");
        }

        if(receiver.getStatus() != WalletStatus.ACTIVE){
            throw new IllegalArgumentException("Receiver wallet is not active");
        }

        // Preserve the original balances before changing them
        BigDecimal senderBalanceBefore = sender.getBalance();
        BigDecimal receiverBalanceBefore = receiver.getBalance();

        sender.debit(amount);
        receiver.credit(amount);

        BigDecimal senderBalanceAfter = sender.getBalance();
        BigDecimal receiverBalanceAfter = receiver.getBalance();

        String referenceId = UUID.randomUUID().toString();

        Transaction debitTransaction = new Transaction(
                referenceId,
                sender,
                TransactionType.DEBIT,
                amount,
                senderBalanceBefore,
                senderBalanceAfter,
                request.description()
        );

        Transaction creditTransaction = new Transaction(
                referenceId,
                receiver,
                TransactionType.CREDIT,
                amount,
                receiverBalanceBefore,
                receiverBalanceAfter,
                request.description()
        );

        walletRepository.save(sender);
        walletRepository.save(receiver);

        transactionRepository.save(debitTransaction);
        transactionRepository.save(creditTransaction);

        return new TransferResponse(
                referenceId,
                sender.getAccountNumber(),
                receiver.getAccountNumber(),
                amount,
                request.description(),
                "SUCCESS"
        );
    }


}
