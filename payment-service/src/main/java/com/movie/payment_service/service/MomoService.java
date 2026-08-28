package com.movie.payment_service.service;

import com.movie.payment_service.dto.MoMoIpnDTO;
import com.movie.payment_service.dto.PaymentTransactionSummaryDTO;
import com.movie.payment_service.dto.RefundResponseDTO;

import java.util.List;

public interface MomoService {
    String createPayment(String userId, String bookingId);
    void processIpn(MoMoIpnDTO dto);
    void testConfirmSuccess(String userId, String bookingId);
    List<PaymentTransactionSummaryDTO> getMyTransactions(String userId);
    RefundResponseDTO refundPayment(String bookingId, String reason);
}
