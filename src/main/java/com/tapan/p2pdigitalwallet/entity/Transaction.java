package com.tapan.p2pdigitalwallet.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;


@Entity
@Immutable
@Table(name = "transactions",
        indexes = {
                @Index(name = "idx_transactions_wallet_created", columnList = "wallet_id, created_at"),
                @Index(name = "idx_transactions_reference_id", columnList = "reference_id")
        },
        check = @CheckConstraint(
                name = "ck_transactions_amount_and_balance",
                constraint = "amount > 0 AND balance_before >= 0 AND balance_after >= 0"
        )
)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, length = 40, updatable = false)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_transactions_wallet"))
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 10, updatable = false)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_before", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Transaction() {
    }


    public Transaction(String referenceId, Wallet wallet, TransactionType transactionType,
                       BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter,
                       String description) {

        this.referenceId = Objects.requireNonNull(referenceId, "Reference ID is required").trim();

        if (this.referenceId.isEmpty() || this.referenceId.length() > 40) {
            throw new IllegalArgumentException("Reference ID must contain 1 to 40 characters");
        }

        this.wallet = Objects.requireNonNull(wallet, "Wallet is required");
        this.transactionType = Objects.requireNonNull(transactionType, "Transaction type is required");
        this.amount = Objects.requireNonNull(amount, "Amount is required");
        this.balanceBefore = Objects.requireNonNull(balanceBefore, "Balance before is required");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "Balance after is required");

        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Transaction amount must be positive");
        }

        if (balanceBefore.signum() < 0 || balanceAfter.signum() < 0) {
            throw new IllegalArgumentException("Transaction balances cannot be negative");
        }

        if (amount.scale() > 2 || balanceBefore.scale() > 2 || balanceAfter.scale() > 2) {
            throw new IllegalArgumentException("Amounts and balances cannot have more than 2 decimal places");
        }

        this.description = description;


        BigDecimal expectedBalanceAfter;

        if (transactionType == TransactionType.CREDIT) {
            expectedBalanceAfter = balanceBefore.add(amount);
        } else {
            expectedBalanceAfter = balanceBefore.subtract(amount);
        }

        if (expectedBalanceAfter.signum() < 0
                || expectedBalanceAfter.compareTo(balanceAfter) != 0) {
            throw new IllegalArgumentException(
                    "Balance after does not match the transaction amount and type"
            );
        }
    }


    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getBalanceBefore() {
        return balanceBefore;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

