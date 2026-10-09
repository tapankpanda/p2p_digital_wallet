package com.tapan.p2pdigitalwallet.repository;

import com.tapan.p2pdigitalwallet.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository  extends JpaRepository<Transaction, Long> {
    Page<Transaction> findByWalletIdOrderByCreatedAtDescIdDesc(Long walletId, Pageable pageable);

    List<Transaction> findByReferenceId(String referenceId);

}
