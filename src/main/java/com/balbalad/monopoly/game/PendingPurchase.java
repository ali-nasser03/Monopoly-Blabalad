package com.balbalad.monopoly.game;

import java.time.Instant;

/**
 * قرار معلّق: لاعب وقف على أرض/مرفق فاضي ولازم يختار "اشترِ" أو
 * "افتح مزاد" قبل ما نكمل الدور. advanceTurnAfter بتحفظ هل كان
 * عنده رمية إضافية (دبل) حتى نرجعها له بعد ما يخلص قراره.
 */
public class PendingPurchase {
    private final int position;
    private final String playerId;
    private final boolean advanceTurnAfter;
    private final Instant deadline;

    public PendingPurchase(int position, String playerId, boolean advanceTurnAfter, Instant deadline) {
        this.position = position;
        this.playerId = playerId;
        this.advanceTurnAfter = advanceTurnAfter;
        this.deadline = deadline;
    }

    public int getPosition() { return position; }
    public String getPlayerId() { return playerId; }
    public boolean isAdvanceTurnAfter() { return advanceTurnAfter; }
    public Instant getDeadline() { return deadline; }
}
