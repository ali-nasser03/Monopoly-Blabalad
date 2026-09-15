package com.balbalad.monopoly.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * حالة لعبة واحدة (بعد ما تبلش). كل القيم هون تتغير أثناء اللعب،
 * فمش سجل ثابت. القفل على هاد الكائن (synchronized) هو يلي بيمنع
 * تعارض رميتين بنفس اللحظة.
 */
public class GameState {

    public static final int STARTING_BALANCE = 1500;

    private final List<String> turnOrder;
    private int currentTurnIndex = 0;

    private final Map<String, Integer> positions = new HashMap<>();
    private final Map<String, Integer> balances = new HashMap<>();
    private final Map<String, Boolean> inJail = new HashMap<>();
    private final Map<String, Integer> jailAttempts = new HashMap<>();
    private final Map<String, Integer> jailFreeCards = new HashMap<>();
    private final Map<String, Boolean> skipNextTurn = new HashMap<>();
    private final Map<String, Integer> missedTurns = new HashMap<>();
    private final Map<Integer, String> ownership = new HashMap<>();
    private final Map<Integer, Integer> houses = new HashMap<>();     // 0-4 دور، 5 = عمارة
    private final Map<Integer, Boolean> mortgaged = new HashMap<>();

    private final List<Card> chanceDeck = new ArrayList<>();
    private final List<Card> chanceDiscard = new ArrayList<>();
    private final List<Card> chestDeck = new ArrayList<>();
    private final List<Card> chestDiscard = new ArrayList<>();
    private final List<Card> gateDeck = new ArrayList<>();
    private final List<Card> gateDiscard = new ArrayList<>();
    private String lastCardText;
    private CardDeckType lastCardDeck;
    private String lastJailPlayerId;
    private long lastJailSeq = 0;

    private PendingPurchase pendingPurchase;
    private AuctionState auction;
    private NegotiationSession negotiation;
    private PendingDebt pendingDebt;
    private PendingCardMove pendingCardMove;
    private boolean hasRolledThisTurn = false;
    private boolean negotiationUsedThisTurn = false;

    private int consecutiveDoubles = 0;
    private int lastDie1 = 0;
    private int lastDie2 = 0;
    private Instant turnDeadline;
    private boolean ended = false;

    private Instant matchEndDeadline;
    private boolean extendVoteActive = false;
    private final Map<String, Boolean> extendVoteResponses = new HashMap<>();
    private Instant extendVoteDeadline;

    // تصويت إنهاء اللعبة (يتطلب إجماع كل اللاعبين المتصلين)
    private boolean voteActive = false;
    private final Map<String, Boolean> voteResponses = new HashMap<>();
    private Instant voteDeadline;

    private final List<String> eventLog = new ArrayList<>();

    public GameState(List<String> playerIdsInOrder, int turnSeconds) {
        this.turnOrder = new ArrayList<>(playerIdsInOrder);
        for (String id : turnOrder) {
            positions.put(id, 0);
            balances.put(id, STARTING_BALANCE);
            inJail.put(id, false);
            jailAttempts.put(id, 0);
            jailFreeCards.put(id, 0);
            skipNextTurn.put(id, false);
            missedTurns.put(id, 0);
        }
        this.turnDeadline = Instant.now().plusSeconds(turnSeconds);
        this.matchEndDeadline = Instant.now().plusSeconds(60L * 60);

        chanceDeck.addAll(CardData.CHANCE);
        Collections.shuffle(chanceDeck);
        chestDeck.addAll(CardData.COMMUNITY_CHEST);
        Collections.shuffle(chestDeck);
        gateDeck.addAll(CardData.GATES);
        Collections.shuffle(gateDeck);
    }

    public String currentPlayerId() { return turnOrder.get(currentTurnIndex); }

    public List<String> getTurnOrder() { return turnOrder; }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public void setCurrentTurnIndex(int i) { this.currentTurnIndex = i; }

    public Map<String, Integer> getPositions() { return positions; }
    public Map<String, Integer> getBalances() { return balances; }
    public Map<String, Boolean> getInJail() { return inJail; }
    public Map<String, Integer> getJailAttempts() { return jailAttempts; }
    public Map<String, Integer> getJailFreeCards() { return jailFreeCards; }
    public Map<String, Boolean> getSkipNextTurn() { return skipNextTurn; }
    public Map<String, Integer> getMissedTurns() { return missedTurns; }
    public Map<Integer, String> getOwnership() { return ownership; }
    public Map<Integer, Integer> getHouses() { return houses; }
    public Map<Integer, Boolean> getMortgaged() { return mortgaged; }

    public List<Card> getChanceDeck() { return chanceDeck; }
    public List<Card> getChanceDiscard() { return chanceDiscard; }
    public List<Card> getChestDeck() { return chestDeck; }
    public List<Card> getChestDiscard() { return chestDiscard; }
    public List<Card> getGateDeck() { return gateDeck; }
    public List<Card> getGateDiscard() { return gateDiscard; }
    public String getLastCardText() { return lastCardText; }
    public CardDeckType getLastCardDeck() { return lastCardDeck; }
    public void setLastCard(String text, CardDeckType deck) { this.lastCardText = text; this.lastCardDeck = deck; }
    public String getLastJailPlayerId() { return lastJailPlayerId; }
    public void setLastJailPlayerId(String id) { this.lastJailPlayerId = id; }
    public long getLastJailSeq() { return lastJailSeq; }
    public void setLastJailSeq(long seq) { this.lastJailSeq = seq; }

    public PendingPurchase getPendingPurchase() { return pendingPurchase; }
    public void setPendingPurchase(PendingPurchase p) { this.pendingPurchase = p; }
    public AuctionState getAuction() { return auction; }
    public void setAuction(AuctionState a) { this.auction = a; }
    public NegotiationSession getNegotiation() { return negotiation; }
    public void setNegotiation(NegotiationSession n) { this.negotiation = n; }
    public PendingDebt getPendingDebt() { return pendingDebt; }
    public void setPendingDebt(PendingDebt d) { this.pendingDebt = d; }
    public PendingCardMove getPendingCardMove() { return pendingCardMove; }
    public void setPendingCardMove(PendingCardMove m) { this.pendingCardMove = m; }
    public boolean isHasRolledThisTurn() { return hasRolledThisTurn; }
    public void setHasRolledThisTurn(boolean v) { this.hasRolledThisTurn = v; }
    public boolean isNegotiationUsedThisTurn() { return negotiationUsedThisTurn; }
    public void setNegotiationUsedThisTurn(boolean v) { this.negotiationUsedThisTurn = v; }

    public int getConsecutiveDoubles() { return consecutiveDoubles; }
    public void setConsecutiveDoubles(int v) { this.consecutiveDoubles = v; }

    public void setLastDice(int d1, int d2) { this.lastDie1 = d1; this.lastDie2 = d2; }
    public int getLastDie1() { return lastDie1; }
    public int getLastDie2() { return lastDie2; }

    public Instant getTurnDeadline() { return turnDeadline; }
    public void setTurnDeadline(Instant t) { this.turnDeadline = t; }

    public boolean isEnded() { return ended; }
    public void setEnded(boolean ended) { this.ended = ended; }

    public Instant getMatchEndDeadline() { return matchEndDeadline; }
    public void setMatchEndDeadline(Instant t) { this.matchEndDeadline = t; }
    public boolean isExtendVoteActive() { return extendVoteActive; }
    public void setExtendVoteActive(boolean v) { this.extendVoteActive = v; }
    public Map<String, Boolean> getExtendVoteResponses() { return extendVoteResponses; }
    public Instant getExtendVoteDeadline() { return extendVoteDeadline; }
    public void setExtendVoteDeadline(Instant t) { this.extendVoteDeadline = t; }

    public boolean isVoteActive() { return voteActive; }
    public void setVoteActive(boolean v) { this.voteActive = v; }
    public Map<String, Boolean> getVoteResponses() { return voteResponses; }
    public Instant getVoteDeadline() { return voteDeadline; }
    public void setVoteDeadline(Instant t) { this.voteDeadline = t; }

    public List<String> getEventLog() { return eventLog; }
}