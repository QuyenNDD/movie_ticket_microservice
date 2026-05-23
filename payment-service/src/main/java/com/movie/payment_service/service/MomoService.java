package com.movie.payment_service.service;

import com.movie.payment_service.dto.MoMoIpnDTO;

public interface MomoService {
    String createPayment(String userId, String bookingId);
    void processIpn(MoMoIpnDTO dto);
}
