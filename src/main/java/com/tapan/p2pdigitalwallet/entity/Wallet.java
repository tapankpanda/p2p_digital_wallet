package com.tapan.p2pdigitalwallet.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_wallets_account_number", columnNames = "account_number"),
            @UniqueConstraint(name = "uk_wallets_user_id", columnNames = "user_id")
        },
        check = @CheckConstraint(
                name = "ck_wallets_balance_non_negative",
                constraint = "balance >= 0"
        )
)

public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_wallets_user"))
    private User user;

    @Column(name = "account_number", nullable = false, length = 12, updatable = false )
    private String accountNumber;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private WalletStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet(){

    }

    public Wallet(User user, String accountNumber, BigDecimal balance) {
        this.user = user;
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = WalletStatus.ACTIVE;
    }

    @PrePersist
    void onCreate(){
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate(){
        this.updatedAt = Instant.now();
    }


    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public void setStatus(WalletStatus status) {
        this.status = status;
    }

    public User getUser() {
        return user;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }


    public Long getId() {
        return id;
    }
}
