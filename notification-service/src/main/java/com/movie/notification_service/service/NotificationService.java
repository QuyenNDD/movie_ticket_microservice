package com.movie.notification_service.service;

import com.movie.notification_service.dto.NotificationResponseDTO;

import java.util.List;

public interface NotificationService {
    NotificationResponseDTO createNotification(String userId, String title, String content, String type);
    List<NotificationResponseDTO> getMyNotifications(String userId);
    long getUnreadCount(String userId);
    NotificationResponseDTO markAsRead(String userId, String notificationId);
}
