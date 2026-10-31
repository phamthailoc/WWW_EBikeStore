package com.ebike.notificationModule.service.impl;

import com.ebike.notificationModule.repository.NotificationRepository;
import com.ebike.notificationModule.service.NotificationStreamService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class NotificationStreamServiceImpl implements NotificationStreamService {

    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final NotificationRepository notificationRepository;
    private final Map<Long, List<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    public NotificationStreamServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emittersByUser.computeIfAbsent(userId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(error -> removeEmitter(userId, emitter));
        sendUnreadCount(userId);
        return emitter;
    }

    @Override
    public void sendUnreadCount(Long userId) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("unread-count")
                    .data(Map.of("unreadCount", unreadCount)));
            } catch (IOException | IllegalStateException exception) {
                removeEmitter(userId, emitter);
            }
        }
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUser.remove(userId);
        }
    }
}
