package com.movie.payment_service.scheduler;

import com.movie.payment_service.entity.PaymentStatus;
import com.movie.payment_service.entity.PaymentTransaction;
import com.movie.payment_service.repository.PaymentTransactionRepository;
import com.movie.payment_service.service.MomoServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PaymentConfirmRetryJob {

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private MomoServiceImpl momoService;

    @Scheduled(fixedDelayString = "${app.payment-retry.fixed-delay-ms:60000}")
    @Transactional
    public void retryConfirmPendingPayments() {
        List<PaymentTransaction> pendingPayments =
                paymentTransactionRepository.findRetryablePayments(
                        PaymentStatus.CONFIRM_PENDING,
                        LocalDateTime.now(),
                        PageRequest.of(0, 20)
                );

        if (pendingPayments.isEmpty()) {
            return;
        }

        for (PaymentTransaction payment : pendingPayments) {
            PaymentTransaction lockedPayment = paymentTransactionRepository
                    .findByIdForUpdate(payment.getId())
                    .orElse(null);

            if (lockedPayment == null) {
                continue;
            }

            if (!PaymentStatus.CONFIRM_PENDING.equals(lockedPayment.getStatus())) {
                continue;
            }

            if (lockedPayment.getNextRetryAt() == null ||
                    lockedPayment.getNextRetryAt().isAfter(LocalDateTime.now())) {
                continue;
            }

            momoService.confirmBookingAndMarkSuccess(lockedPayment);
        }
    }
}