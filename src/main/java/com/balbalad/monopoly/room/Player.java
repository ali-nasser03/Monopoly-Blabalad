package com.balbalad.monopoly.room;

import java.time.Instant;

/**
 * لاعب واحد داخل غرفة. الحالة (متصل/آخر ظهور) بتتغير أثناء حياة
 * الغرفة (دخول، انقطاع، إعادة اتصال)، فمش سجل ثابت (record).
 */
public class Player {

    private final String id;
    private final String name;
    private final Piece piece;
    private final boolean host;
    private final boolean bot;
    private volatile boolean connected;
    private volatile Instant lastSeen;

    public Player(String id, String name, Piece piece, boolean host) {
        this(id, name, piece, host, false);
    }

    public Player(String id, String name, Piece piece, boolean host, boolean bot) {
        this.id = id;
        this.name = name;
        this.piece = piece;
        this.host = host;
        this.bot = bot;
        this.connected = true;
        this.lastSeen = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public Piece getPiece() { return piece; }
    public boolean isHost() { return host; }
    public boolean isBot() { return bot; }
    public boolean isConnected() { return connected; }
    public Instant getLastSeen() { return lastSeen; }

    public void setConnected(boolean connected) { this.connected = connected; }

    /** يحدّث آخر لحظة ظهور - يُستدعى عند كل اتصال/دخول/إعادة دخول. */
    public void touch() { this.lastSeen = Instant.now(); }
}