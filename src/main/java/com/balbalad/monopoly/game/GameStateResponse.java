package com.balbalad.monopoly.game;

import com.balbalad.monopoly.board.BoardData;
import com.balbalad.monopoly.board.BoardSquare;
import com.balbalad.monopoly.board.ColorGroup;
import com.balbalad.monopoly.board.SquareType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record GameStateResponse(
        String currentTurnPlayerId,
        List<String> turnOrder,
        Map<String, Integer> positions,
        Map<String, Boolean> inJail,
        Map<String, Integer> jailAttempts,
        Map<String, Integer> jailFreeCards,
        Map<Integer, String> ownership,
        Map<Integer, Integer> houses,
        Map<Integer, Boolean> mortgaged,
        int lastDie1,
        int lastDie2,
        int consecutiveDoubles,
        long turnDeadlineEpochMs,
        PendingPurchaseView pendingPurchase,
        AuctionView auction,
        NegotiationView negotiation,
        PendingDebtView pendingDebt,
        boolean hasRolledThisTurn,
        boolean negotiationUsedThisTurn,
        boolean canNegotiate,
        String lastCardText,
        String lastCardDeck,
        boolean ended,
        boolean voteActive,
        Map<String, Boolean> voteResponses,
        long voteDeadlineEpochMs,
        long matchEndDeadlineEpochMs,
        boolean extendVoteActive,
        Map<String, Boolean> extendVoteResponses,
        long extendVoteDeadlineEpochMs,
        Map<String, Integer> finalValues,
        String winnerId,
        List<String> eventLog
) {
    public record PendingPurchaseView(int position, String playerId, long deadlineEpochMs) {}

    public record AuctionView(int position, String openerPlayerId, int openingPrice,
                               Integer currentBid, String currentBidderId, long deadlineEpochMs) {}

    public record NegotiationView(String initiatorId, String counterpartId, int offerCash,
                                   java.util.List<Integer> offerProperties, int requestCash,
                                   java.util.List<Integer> requestProperties, long deadlineEpochMs) {}

    public record PendingDebtView(String playerId, String creditorId, int amountOwed, long deadlineEpochMs) {}

    static GameStateResponse from(GameState s) {
        PendingPurchaseView ppView = null;
        if (s.getPendingPurchase() != null) {
            PendingPurchase pp = s.getPendingPurchase();
            ppView = new PendingPurchaseView(pp.getPosition(), pp.getPlayerId(), pp.getDeadline().toEpochMilli());
        }
        AuctionView auctionView = null;
        if (s.getAuction() != null) {
            AuctionState a = s.getAuction();
            auctionView = new AuctionView(a.getPosition(), a.getOpenerPlayerId(), a.getOpeningPrice(),
                    a.getCurrentBid(), a.getCurrentBidderId(), a.getDeadline().toEpochMilli());
        }
        NegotiationView negotiationView = null;
        if (s.getNegotiation() != null) {
            NegotiationSession n = s.getNegotiation();
            negotiationView = new NegotiationView(n.getInitiatorId(), n.getCounterpartId(), n.getOfferCash(),
                    n.getOfferProperties(), n.getRequestCash(), n.getRequestProperties(), n.getDeadline().toEpochMilli());
        }
        PendingDebtView debtView = null;
        if (s.getPendingDebt() != null) {
            PendingDebt d = s.getPendingDebt();
            debtView = new PendingDebtView(d.getPlayerId(), d.getCreditorId(), d.getAmountOwed(), d.getDeadline().toEpochMilli());
        }
        boolean canNegotiate = com.balbalad.monopoly.board.BoardData.SQUARES.stream()
                .filter(sq -> sq.type() == com.balbalad.monopoly.board.SquareType.PROPERTY
                        || sq.type() == com.balbalad.monopoly.board.SquareType.UTILITY)
                .allMatch(sq -> s.getOwnership().containsKey(sq.position()));

        Map<String, Integer> finalValues = null;
        String winnerId = null;
        if (s.isEnded()) {
            finalValues = computeFinalValues(s);
            int max = finalValues.values().stream().mapToInt(v -> v).max().orElse(0);
            List<String> topPlayers = finalValues.entrySet().stream()
                    .filter(e -> e.getValue() == max)
                    .map(Map.Entry::getKey)
                    .toList();
            winnerId = topPlayers.size() == 1 ? topPlayers.get(0) : null; // تعادل = بدون فائز
        }

        return new GameStateResponse(
                s.currentPlayerId(),
                List.copyOf(s.getTurnOrder()),
                Map.copyOf(s.getPositions()),
                Map.copyOf(s.getInJail()),
                Map.copyOf(s.getJailAttempts()),
                Map.copyOf(s.getJailFreeCards()),
                Map.copyOf(s.getOwnership()),
                Map.copyOf(s.getHouses()),
                Map.copyOf(s.getMortgaged()),
                s.getLastDie1(),
                s.getLastDie2(),
                s.getConsecutiveDoubles(),
                s.getTurnDeadline() == null ? 0 : s.getTurnDeadline().toEpochMilli(),
                ppView,
                auctionView,
                negotiationView,
                debtView,
                s.isHasRolledThisTurn(),
                s.isNegotiationUsedThisTurn(),
                canNegotiate,
                s.getLastCardText(),
                s.getLastCardDeck() == null ? null : s.getLastCardDeck().name(),
                s.isEnded(),
                s.isVoteActive(),
                Map.copyOf(s.getVoteResponses()),
                s.getVoteDeadline() == null ? 0 : s.getVoteDeadline().toEpochMilli(),
                s.getMatchEndDeadline() == null ? 0 : s.getMatchEndDeadline().toEpochMilli(),
                s.isExtendVoteActive(),
                Map.copyOf(s.getExtendVoteResponses()),
                s.getExtendVoteDeadline() == null ? 0 : s.getExtendVoteDeadline().toEpochMilli(),
                finalValues,
                winnerId,
                List.copyOf(s.getEventLog())
        );
    }

    /** القيمة الإجمالية = النقد + سعر كل أرض/مرفق تملكه + تكلفة كل بناء دفعتها - قيمة أي رهن قائم (القسم 13). */
    private static Map<String, Integer> computeFinalValues(GameState s) {
        Map<String, Integer> values = new HashMap<>();
        for (String playerId : s.getTurnOrder()) {
            int total = s.getBalances().getOrDefault(playerId, 0);
            for (Map.Entry<Integer, String> e : s.getOwnership().entrySet()) {
                if (!playerId.equals(e.getValue())) continue;
                int pos = e.getKey();
                BoardSquare sq = BoardData.SQUARES.get(pos);
                total += sq.price();
                if (sq.type() == SquareType.PROPERTY && sq.colorGroup() != ColorGroup.NONE) {
                    int houseLevel = s.getHouses().getOrDefault(pos, 0);
                    int builtCount = Math.min(houseLevel, 5);
                    total += builtCount * sq.colorGroup().getHouseCost();
                }
                if (Boolean.TRUE.equals(s.getMortgaged().get(pos))) {
                    total -= sq.price() / 2;
                }
            }
            values.put(playerId, total);
        }
        return values;
    }
}
