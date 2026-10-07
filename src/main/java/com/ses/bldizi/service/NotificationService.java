package com.ses.bldizi.service;

import com.ses.bldizi.model.Notification;
import com.ses.bldizi.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void createNotification(String title, String message, UUID contentId, String type) {
        Notification notification = new Notification(title, message, contentId, type);
        repository.save(notification);
    }

    public List<Notification> getRecentNotifications(int limit) {
        return repository.findRecent(limit);
    }

    public void markAsRead(UUID id) {
        repository.markAsRead(id);
    }

    public int getUnreadCount() {
        return repository.getUnreadCount();
    }
}
