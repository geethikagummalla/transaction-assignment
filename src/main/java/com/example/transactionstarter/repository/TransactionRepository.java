package com.example.transactionstarter.repository;

import com.example.transactionstarter.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByCustomerId(String customerId);

    boolean existsByTransactionId(String transactionId);
}
