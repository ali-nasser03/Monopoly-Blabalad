package com.balbalad.monopoly.game;

import java.time.Instant;

/**
 * مزاد جاري على أرض/مرفق. فاتح المزاد (يلي وقف على الأرض ورفض
 * الشراء المباشر) ما بيزاود (القسم 7 من وثيقة المواصفات).
 */
public class AuctionState {
    private final int position;
    private final String openerPlayerId;
    private final int openingPrice;
    private final boolean advanceTurnAfter;

    private Integer currentBid;
    private String currentBidderId;
    private Instant deadline;

    public AuctionState(int position, String openerPlayerId, int openingPrice,
                         boolean advanceTurnAfter, Instant deadline) {
        this.position = position;
        this.openerPlayerId = openerPlayerId;
        this.openingPrice = openingPrice;
        this.advanceTurnAfter = advanceTurnAfter;
        this.deadline = deadline;
    }

    public int getPosition() { return position; }
    public String getOpenerPlayerId() { return openerPlayerId; }
    public int getOpeningPrice() { return openingPrice; }
    public boolean isAdvanceTurnAfter() { return advanceTurnAfter; }

    public Integer getCurrentBid() { return currentBid; }
    public void setCurrentBid(Integer currentBid) { this.currentBid = currentBid; }
    public String getCurrentBidderId() { return currentBidderId; }
    public void setCurrentBidderId(String id) { this.currentBidderId = id; }
    public Instant getDeadline() { return deadline; }
    public void setDeadline(Instant deadline) { this.deadline = deadline; }
}
