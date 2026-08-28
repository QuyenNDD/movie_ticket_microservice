package com.movie.payment_service.repository;

import com.movie.payment_service.entity.RefundTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefundTransactionRepository extends JpaRepository<RefundTransaction, String> {
    Optional<RefundTransaction> findByPaymentTransactionId(String paymentTransactionId);
}
