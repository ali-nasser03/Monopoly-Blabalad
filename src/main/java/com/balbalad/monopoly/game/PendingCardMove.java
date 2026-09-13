package com.balbalad.monopoly.game;

import java.time.Instant;

/**
 * لما بطاقة تطلب حركة إضافية (MOVE_TO أو MOVE_RELATIVE)، منوقف هون
 * أول: نبث اللاعب لسا واقف على خانة البطاقة (مع نص البطاقة)، وبعد
 * فترة قصيرة الفحص الدوري بينفّذ الحركة الفعلية وبيبث تاني. هيك
 * القطعة بتوصل فعليًا للبطاقة، تطلع البطاقة، وبعدين تبلش حركتها -
 * مش كلشي بقفزة وحدة.
 */
public class PendingCardMove {
    private final String playerId;
    private final Card card;
    private final int diceSum;
    private final boolean advanceAfter;
    private final Instant resolveAt;

    public PendingCardMove(String playerId, Card card, int diceSum, boolean advanceAfter, Instant resolveAt) {
        this.playerId = playerId;
        this.card = card;
        this.diceSum = diceSum;
        this.advanceAfter = advanceAfter;
        this.resolveAt = resolveAt;
    }

    public String getPlayerId() { return playerId; }
    public Card getCard() { return card; }
    public int getDiceSum() { return diceSum; }
    public boolean isAdvanceAfter() { return advanceAfter; }
    public Instant getResolveAt() { return resolveAt; }
}