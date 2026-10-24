package com.tapan.p2pdigitalwallet.service;

import com.tapan.p2pdigitalwallet.dto.*;
import com.tapan.p2pdigitalwallet.entity.*;
import com.tapan.p2pdigitalwallet.exception.ConflictException;
import com.tapan.p2pdigitalwallet.exception.ResourceNotFoundException;
import com.tapan.p2pdigitalwallet.repository.IdempotencyRecordRepository;
import com.tapan.p2pdigitalwallet.repository.TransactionRepository;
import com.tapan.p2pdigitalwallet.repository.UserRepository;
import com.tapan.p2pdigitalwallet.repository.WalletRepository;
import com.tapan.p2pdigitalwallet.util.AccountNumberGenerator;
import com.tapan.p2pdigitalwallet.util.TransferRequestHasher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;


@Service
public class WalletService {

    private static final BigDecimal MAX_TRANSFER_AMOUNT =
            new BigDecimal("999999999999999.99");

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final TransactionRepository transactionRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public WalletService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            AccountNumberGenerator accountNumberGenerator,
            TransactionRepository transactionRepository,
            IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.transactionRepository = transactionRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }


    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request){
        String email = User.normalizeEmail(request.email());
        if(userRepository.existsByEmail(email)){
            throw new ConflictException("A user with this email already has a wallet.");
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
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wallet not found"));
        return WalletResponse.from(wallet);
    }

    // Retrieve wallet balance
    @Transactional (readOnly = true)
    public BalanceResponse getBalance(String accountNumber){
        Wallet wallet = walletRepository
                .findByAccountNumber(accountNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wallet not found"));
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
                .orElseThrow(() ->
                        new ResourceNotFoundException("Wallet not found"));

        return transactionRepository
                .findByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId(), pageable)
                .map(TransactionResponse::from);
    }

    // Transfer money between two wallets
    @Transactional
    public TransferResponse transfer(
            String idempotencyKey,
            TransferRequest request){

        // validate the idempotency key
        if(idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100){
            throw new IllegalArgumentException(
                    "Idempotency key must contain between 1 and 100 characters"
            );
        }

        // validate the amount before hashing the request
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

        // Create a hash of the request to detect duplicate or modified requests
        String requestHash = TransferRequestHasher.hash(request);

        var existingRecord = idempotencyRecordRepository
                .findByIdempotencyKey(idempotencyKey);

        // Return the previous result if this transfer has already been processed
        if(existingRecord.isPresent()){
            IdempotencyRecord record = existingRecord.get();

            if(!record.getRequestHash().equals(requestHash)){
                throw new ConflictException(
                        "Idempotency key has already been used with a different request"
                );
            }

            if(!"SUCCESS".equals(record.getStatus())){
                throw new ConflictException(
                        "The previous request did not complete successfully"
                );
            }
            return getOriginalTransferResponse(record);
        }

        // Get only the IDs before locking either wallet
        Long senderId = walletRepository
                .findIdByAccountNumber(request.fromAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Sender wallet not found"));

        Long receiverId = walletRepository
                .findIdByAccountNumber(request.toAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Receiver wallet not found"));

        Wallet firstLocked;
        Wallet secondLocked;

        // Lock wallets in ascending ID order to reduce deadlock risk
        if(senderId <receiverId){
            firstLocked = walletRepository
                    .findByIdForUpdate(senderId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Sender wallet not found"));

            secondLocked = walletRepository
                    .findByIdForUpdate(receiverId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Receiver wallet not found"));
        }
        else{
            firstLocked = walletRepository
                    .findByIdForUpdate(receiverId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Receiver wallet not found"));

            secondLocked = walletRepository
                    .findByIdForUpdate(senderId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Sender wallet not found"));
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

        // Create matching debit and credit ledger entries
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

        // Store the idempotency record to prevent duplicate transfers on retries
        idempotencyRecordRepository.save(
                new IdempotencyRecord(
                        idempotencyKey,
                        requestHash,
                        referenceId,
                        "SUCCESS"
                )
        );

        return new TransferResponse(
                referenceId,
                sender.getAccountNumber(),
                receiver.getAccountNumber(),
                amount,
                request.description(),
                "SUCCESS"
        );
    }

    // Reconstruct the original response for a retry
    private TransferResponse getOriginalTransferResponse(
            IdempotencyRecord record
    ) {
        List<Transaction> transactions =
                transactionRepository.findByReferenceId(
                        record.getReferenceId()
                );

        Transaction debit = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.DEBIT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Original transfer debit transaction was not found"
                ));

        Transaction credit = transactions.stream()
                .filter(t -> t.getTransactionType() == TransactionType.CREDIT)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Original transfer credit transaction was not found"
                ));

        return new TransferResponse(
                record.getReferenceId(),
                debit.getWallet().getAccountNumber(),
                credit.getWallet().getAccountNumber(),
                debit.getAmount(),
                debit.getDescription(),
                record.getStatus()
        );
    }

}
