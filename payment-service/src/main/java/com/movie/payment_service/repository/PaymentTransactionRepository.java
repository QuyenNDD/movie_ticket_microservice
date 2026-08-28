package com.movie.payment_service.repository;

import com.movie.payment_service.entity.PaymentStatus;
import com.movie.payment_service.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, String> {

    Optional<PaymentTransaction> findByBookingId(String bookingId);

    Optional<PaymentTransaction> findByOrderId(String orderId);

    Optional<PaymentTransaction> findByTransId(String transId);

    boolean existsByTransIdAndStatus(String transId, PaymentStatus status);

    List<PaymentTransaction> findByUserIdOrderByCreatedAtDesc(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.orderId = :orderId")
    Optional<PaymentTransaction> findByOrderIdForUpdate(@Param("orderId") String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentTransaction p WHERE p.id = :id")
    Optional<PaymentTransaction> findByIdForUpdate(@Param("id") String id);

    @Query("SELECT p FROM PaymentTransaction p " +
            "WHERE p.status = :status " +
            "AND p.nextRetryAt <= :now " +
            "ORDER BY p.updatedAt ASC")
    List<PaymentTransaction> findRetryablePayments(
            @Param("status") PaymentStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}