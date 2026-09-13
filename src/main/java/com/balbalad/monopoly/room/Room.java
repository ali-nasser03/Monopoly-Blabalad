package com.balbalad.monopoly.room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * غرفة لعب واحدة (القسم 3). تعيش بالذاكرة طول ما السيرفر شغال؛
 * ما في قاعدة بيانات لهاد الجزء لأن الغرف مؤقتة (ساعة كحد أقصى
 * تقريبًا حسب مدة المباراة بالقسم 6).
 */
public class Room {

    private final String code;
    private final int maxPlayers;
    private final List<Player> players = new ArrayList<>();
    private volatile boolean started = false;
    private final Instant createdAt = Instant.now();

    public Room(String code, int maxPlayers) {
        this.code = code;
        this.maxPlayers = maxPlayers;
    }

    public String getCode() { return code; }
    public int getMaxPlayers() { return maxPlayers; }
    public List<Player> getPlayers() { return players; }
    public boolean isStarted() { return started; }
    public void setStarted(boolean started) { this.started = started; }
    public Instant getCreatedAt() { return createdAt; }
}
