package com.balbalad.monopoly.game;

import java.time.Instant;

/**
 * دين معلق على لاعب - مبلغ لازم يتغطى (برهن أو هدم) قبل ما يكمل
 * دوره. creditorId فاضي (null) يعني الدين للبنك، وإلا هو للاعب
 * صاحب الإيجار/الصفقة. advanceTurnAfter بتحفظ هل كان عنده رمية
 * إضافية (دبل) حتى نرجعها له بعد ما يسدد.
 */
public class PendingDebt {
    private final String playerId;
    private final String creditorId; // null = دين للبنك
    private final int amountOwed;
    private final boolean advanceTurnAfter;
    private final Instant deadline;

    public PendingDebt(String playerId, String creditorId, int amountOwed,
                        boolean advanceTurnAfter, Instant deadline) {
        this.playerId = playerId;
        this.creditorId = creditorId;
        this.amountOwed = amountOwed;
        this.advanceTurnAfter = advanceTurnAfter;
        this.deadline = deadline;
    }

    public String getPlayerId() { return playerId; }
    public String getCreditorId() { return creditorId; }
    public int getAmountOwed() { return amountOwed; }
    public boolean isAdvanceTurnAfter() { return advanceTurnAfter; }
    public Instant getDeadline() { return deadline; }
}
