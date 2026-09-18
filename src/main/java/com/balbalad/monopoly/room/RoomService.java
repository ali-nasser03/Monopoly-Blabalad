package com.balbalad.monopoly.room;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * إدارة الغرف بالذاكرة (القسم 3 من وثيقة المواصفات). كل التعديلات
 * على غرفة معيّنة تصير جوا synchronized(room) لتفادي تعارض دخول
 * لاعبين بنفس اللحظة.
 */
@Service
public class RoomService {

    private static final Duration RECONNECT_GRACE = Duration.ofMinutes(5);

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final SimpMessagingTemplate messaging;

    public RoomService(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    public JoinResult createRoom(String hostName, Piece piece, int maxPlayers) {
        String name = validateName(hostName);
        if (piece == null) throw bad("لازم تختار قطعة");
        if (maxPlayers < 2 || maxPlayers > 4) throw bad("عدد اللاعبين لازم يكون بين 2 و4");

        String code = generateUniqueCode();
        Room room = new Room(code, maxPlayers);
        Player host = new Player(UUID.randomUUID().toString(), name, piece, true);
        room.getPlayers().add(host);
        rooms.put(code, room);
        return new JoinResult(host, room);
    }

    public JoinResult joinRoom(String code, String name, Piece piece) {
        Room room = getRoomOrThrow(code);
        if (piece == null) throw bad("لازم تختار قطعة");
        String cleanName = validateName(name);

        synchronized (room) {
            // إعادة اتصال: نفس الاسم ونفس القطعة للاعب موجود مسبقًا بالغرفة.
            // ملاحظة مهمة: ما بنشترط إنه يكون مسجّل "منقطع" مسبقًا، لأنه
            // ممكن يرجع بسرعة قبل ما السيرفر يوصله حدث انقطاع WebSocket
            // (خصوصًا لو سكر التاب فجأة). المطابقة بالاسم والقطعة كافية
            // لاعتباره نفس اللاعب الراجع، سواء كانت اللعبة بلشت أو لسا بالانتظار.
            Optional<Player> existing = room.getPlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(cleanName) && p.getPiece() == piece)
                    .findFirst();
            if (existing.isPresent()) {
                Player p = existing.get();
                p.setConnected(true);
                p.touch();
                broadcast(room);
                return new JoinResult(p, room);
            }

            if (room.isStarted()) {
                throw bad("اللعبة بدأت، ما في مجال تنضم للغرفة هاي");
            }

            purgeExpired(room);

            if (room.getPlayers().size() >= room.getMaxPlayers()) {
                throw bad("الغرفة كاملة");
            }
            boolean nameTaken = room.getPlayers().stream()
                    .anyMatch(p -> p.getName().equalsIgnoreCase(cleanName));
            if (nameTaken) {
                throw bad("في لاعب بنفس الاسم بالغرفة، اختر اسم تاني");
            }
            boolean pieceTaken = room.getPlayers().stream().anyMatch(p -> p.getPiece() == piece);
            if (pieceTaken) {
                throw bad("هاي القطعة مأخوذة، اختر وحدة تانية");
            }

            Player newPlayer = new Player(UUID.randomUUID().toString(), cleanName, piece, false);
            room.getPlayers().add(newPlayer);
            broadcast(room);
            return new JoinResult(newPlayer, room);
        }
    }

    public Room startGame(String code, String playerId) {
        Room room = getRoomOrThrow(code);
        synchronized (room) {
            Player requester = findPlayer(room, playerId);
            if (!requester.isHost()) {
                throw bad("بس صاحب الغرفة يقدر يبدأ اللعبة");
            }
            if (room.isStarted()) {
                throw bad("اللعبة بلشت مسبقًا");
            }
            if (room.getPlayers().size() < 2) {
                throw bad("لازم لاعبين اثنين ع الأقل حتى تبلش");
            }
            room.setStarted(true);
            broadcast(room);
            return room;
        }
    }

    /** يُستدعى لما ينقطع اتصال WebSocket تبع لاعب (القسم 3: إعادة الدخول خلال 5 دقائق). */
    public void markDisconnected(String code, String playerId) {
        Room room = rooms.get(code);
        if (room == null) return;
        synchronized (room) {
            room.getPlayers().stream()
                    .filter(p -> p.getId().equals(playerId))
                    .findFirst()
                    .ifPresent(p -> {
                        p.setConnected(false);
                        p.touch();
                        broadcast(room);
                    });
        }
    }

    public Room addBot(String code, String requesterId) {
        Room room = getRoomOrThrow(code);
        synchronized (room) {
            Player requester = findPlayer(room, requesterId);
            if (!requester.isHost()) throw bad("بس صاحب الغرفة يقدر يضيف بوت");
            if (room.isStarted()) throw bad("اللعبة بلشت مسبقًا، ما فيك تضيف بوت هلق");
            if (room.getPlayers().size() >= room.getMaxPlayers()) throw bad("الغرفة كاملة");

            Set<Piece> free = availablePieces(room);
            if (free.isEmpty()) throw bad("ما في قطع فاضية تنعطى للبوت");
            Piece piece = free.iterator().next();

            long botCount = room.getPlayers().stream().filter(Player::isBot).count();
            String botName = "بوت " + (botCount + 1);

            Player bot = new Player(UUID.randomUUID().toString(), botName, piece, false, true);
            room.getPlayers().add(bot);
            broadcast(room);
            return room;
        }
    }

    public Room getRoomOrThrow(String code) {
        Room room = rooms.get(code == null ? "" : code.trim());
        if (room == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "ما في غرفة بهاد الكود");
        return room;
    }

    public Set<Piece> availablePieces(Room room) {
        EnumSet<Piece> taken = EnumSet.noneOf(Piece.class);
        room.getPlayers().stream().filter(Player::isConnected).forEach(p -> taken.add(p.getPiece()));
        EnumSet<Piece> free = EnumSet.allOf(Piece.class);
        free.removeAll(taken);
        return free;
    }

    private Player findPlayer(Room room, String playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> bad("لاعب غير معروف بهاي الغرفة"));
    }

    private void purgeExpired(Room room) {
        Instant cutoff = Instant.now().minus(RECONNECT_GRACE);
        room.getPlayers().removeIf(p -> !p.isConnected() && p.getLastSeen().isBefore(cutoff));
    }

    private String generateUniqueCode() {
        String code;
        do {
            code = String.format("%04d", random.nextInt(10_000));
        } while (rooms.containsKey(code));
        return code;
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) throw bad("لازم تكتب اسم");
        String trimmed = name.trim();
        if (trimmed.length() > 20) throw bad("الاسم طويل كتير، لازم يكون أقصر من 20 حرف");
        return trimmed;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private void broadcast(Room room) {
        messaging.convertAndSend("/topic/rooms/" + room.getCode(),
                RoomStateResponse.from(room, availablePieces(room)));
    }

    /** تنظيف دوري: يشيل لاعبين تجاوزوا مهلة الخمس دقائق، ويحذف الغرف الفاضية يلي ما بلشت. */
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredRoomsAndPlayers() {
        Instant cutoff = Instant.now().minus(RECONNECT_GRACE);
        Iterator<Map.Entry<String, Room>> it = rooms.entrySet().iterator();
        while (it.hasNext()) {
            Room room = it.next().getValue();
            synchronized (room) {
                boolean changed = room.getPlayers().removeIf(p -> !p.isConnected() && p.getLastSeen().isBefore(cutoff));
                if (room.getPlayers().isEmpty() && !room.isStarted()) {
                    it.remove();
                } else if (changed) {
                    broadcast(room);
                }
            }
        }
    }
}