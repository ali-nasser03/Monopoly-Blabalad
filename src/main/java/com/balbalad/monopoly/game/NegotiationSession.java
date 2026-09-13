package com.balbalad.monopoly.game;

import java.time.Instant;
import java.util.List;

/**
 * عرض تفاوض مُرسل من initiatorId لـ counterpartId. offer* هي اللي
 * initiator رح يديها، request* هي اللي initiator طالبها من الطرف
 * التاني (يعني هاي اللي counterpart رح يديها لو وافق).
 */
public class NegotiationSession {
    private final String initiatorId;
    private final String counterpartId;
    private final int offerCash;
    private final List<Integer> offerProperties;
    private final int requestCash;
    private final List<Integer> requestProperties;
    private final Instant deadline;

    public NegotiationSession(String initiatorId, String counterpartId, int offerCash,
                               List<Integer> offerProperties, int requestCash,
                               List<Integer> requestProperties, Instant deadline) {
        this.initiatorId = initiatorId;
        this.counterpartId = counterpartId;
        this.offerCash = offerCash;
        this.offerProperties = offerProperties;
        this.requestCash = requestCash;
        this.requestProperties = requestProperties;
        this.deadline = deadline;
    }

    public String getInitiatorId() { return initiatorId; }
    public String getCounterpartId() { return counterpartId; }
    public int getOfferCash() { return offerCash; }
    public List<Integer> getOfferProperties() { return offerProperties; }
    public int getRequestCash() { return requestCash; }
    public List<Integer> getRequestProperties() { return requestProperties; }
    public Instant getDeadline() { return deadline; }
}
