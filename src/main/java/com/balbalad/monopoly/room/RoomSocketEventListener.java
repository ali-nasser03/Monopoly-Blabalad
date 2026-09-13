package com.balbalad.monopoly.room;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * بيربط sessionId تبع WebSocket باللاعب صاحبها، حتى لما تنقطع
 * الجلسة (إغلاق التاب، فقدان الشبكة...) نعرف نحدد مين انقطع
 * ونعلّمه كـ"غير متصل" (القسم 3: مهلة إعادة الدخول 5 دقائق).
 *
 * بنتتبع كمان آخر جلسة نشطة مسجّلة لكل لاعب (activeSessionByPlayerKey)،
 * حتى لو رجع اتصل بجلسة جديدة بالضبط قبل ما توصل رسالة انقطاع جلسته
 * القديمة، ما نعلّمه "منقطع" غلط بعد ما يكون أصلًا رجع.
 */
@Component
public class RoomSocketEventListener {

    private record PlayerRef(String roomCode, String playerId) {}

    private final Map<String, PlayerRef> sessionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> activeSessionByPlayerKey = new ConcurrentHashMap<>();
    private final RoomService roomService;

    public RoomSocketEventListener(RoomService roomService) {
        this.roomService = roomService;
    }

    public void registerSession(String sessionId, String roomCode, String playerId) {
        sessionToPlayer.put(sessionId, new PlayerRef(roomCode, playerId));
        activeSessionByPlayerKey.put(key(roomCode, playerId), sessionId);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        PlayerRef ref = sessionToPlayer.remove(sessionId);
        if (ref == null) return;

        String key = key(ref.roomCode(), ref.playerId());
        // بس علّمه "منقطع" لو هاي الجلسة لسا هي الأحدث المسجّلة إله.
        // لو رجع سجّل جلسة أجد قبل ما توصل هالرسالة، تجاهلها.
        if (sessionId.equals(activeSessionByPlayerKey.get(key))) {
            roomService.markDisconnected(ref.roomCode(), ref.playerId());
            activeSessionByPlayerKey.remove(key);
        }
    }

    private String key(String roomCode, String playerId) {
        return roomCode + "|" + playerId;
    }
}
