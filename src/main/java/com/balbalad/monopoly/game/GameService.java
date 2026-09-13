package com.balbalad.monopoly.game;

import com.balbalad.monopoly.board.BoardData;
import com.balbalad.monopoly.board.BoardSquare;
import com.balbalad.monopoly.board.ColorGroup;
import com.balbalad.monopoly.board.SquareType;
import com.balbalad.monopoly.room.Player;
import com.balbalad.monopoly.room.Room;
import com.balbalad.monopoly.room.RoomService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * محرك اللعب الفعلي: نرد، حركة، تمرير دور، مهلة دور، شراء/مزاد
 * على الأراضي والمرافق، وتصويت إنهاء اللعبة. حالة كل غرفة بالذاكرة،
 * تتبنى لما يدوس المضيف "بدء اللعبة" (initGame).
 */
@Service
public class GameService {

    private static final int TURN_SECONDS = 45;
    private static final int PURCHASE_DECISION_SECONDS = 20;
    private static final int AUCTION_SECONDS = 10;
    private static final int MIN_BID_INCREMENT = 10;
    private static final int VOTE_SECONDS = 30;
    private static final int NEGOTIATION_SECONDS = 30;
    private static final int DEBT_SECONDS = 45;
    private static final int CARD_MOVE_DELAY_SECONDS = 4;
    private static final int PASS_GO_BONUS = 200;
    private static final int JAIL_POSITION = 10;
    private static final int GO_TO_JAIL_POSITION = 30;
    private static final int BOARD_SIZE = 40;

    private final Map<String, GameState> games = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messaging;
    private final RoomService roomService;
    private final SecureRandom random = new SecureRandom();

    public GameService(SimpMessagingTemplate messaging, RoomService roomService) {
        this.messaging = messaging;
        this.roomService = roomService;
    }

    public GameState initGame(Room room) {
        List<String> order = room.getPlayers().stream().map(Player::getId).collect(Collectors.toList());
        GameState state = new GameState(order, TURN_SECONDS);
        games.put(room.getCode(), state);
        logEvent(state, "بدأت اللعبة! 🎲");
        broadcast(room.getCode(), state);
        return state;
    }

    public GameState getStateOrThrow(String code) {
        GameState s = games.get(code);
        if (s == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "اللعبة لسا ما بلشت بهاي الغرفة");
        return s;
    }

    // ---------------- النرد والحركة ----------------

    public GameState roll(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (state.isEnded()) throw bad("اللعبة خلصت");
            if (state.getPendingPurchase() != null || state.getAuction() != null) {
                throw bad("في قرار شراء أو مزاد لازم يخلص أول");
            }
            if (state.getNegotiation() != null) {
                throw bad("في عرض تفاوض شغال لازم يخلص أول");
            }
            if (state.getPendingDebt() != null) {
                throw bad("في دين معلق لازم يتسدد أول");
            }
            if (state.getPendingCardMove() != null) {
                throw bad("لسا في حركة بطاقة ما خلصت");
            }
            if (!state.currentPlayerId().equals(playerId)) throw bad("مش دورك هلأ");

            state.setHasRolledThisTurn(true);

            int d1 = 1 + random.nextInt(6);
            int d2 = 1 + random.nextInt(6);
            state.setLastDice(d1, d2);
            boolean isDouble = d1 == d2;

            boolean wasInJail = Boolean.TRUE.equals(state.getInJail().get(playerId));
            boolean advanceAfter;

            if (wasInJail) {
                if (isDouble) {
                    state.getInJail().put(playerId, false);
                    state.getJailAttempts().put(playerId, 0);
                    movePlayer(state, room, playerId, d1 + d2);
                } else {
                    int attempts = state.getJailAttempts().merge(playerId, 1, Integer::sum);
                    if (attempts >= 3) {
                        // 3 محاولات فاشلة: يدفع 50 ويطلع تلقائيًا (دوره القادم عادي)
                        state.getInJail().put(playerId, false);
                        state.getJailAttempts().put(playerId, 0);
                        state.getBalances().merge(playerId, -50, Integer::sum);
                        logEvent(state, playerName(room, playerId) + " دفع ₪50 وطلع من المسكوبية بعد 3 محاولات");
                    }
                }
                state.setConsecutiveDoubles(0);
                advanceAfter = true;
            } else if (isDouble) {
                state.setConsecutiveDoubles(state.getConsecutiveDoubles() + 1);
                if (state.getConsecutiveDoubles() >= 3) {
                    sendToJail(state, room, playerId);
                    state.setConsecutiveDoubles(0);
                    advanceAfter = true;
                } else {
                    movePlayer(state, room, playerId, d1 + d2);
                    advanceAfter = false;
                }
            } else {
                state.setConsecutiveDoubles(0);
                movePlayer(state, room, playerId, d1 + d2);
                advanceAfter = true;
            }

            state.getMissedTurns().put(playerId, 0);

            if (!wasInJail || isDouble) {
                // لو ضل بالسجن (حاول ولا طلع)، ما في خانة جديدة نحسم نتيجتها
                resolveLandingConsequences(state, room, playerId, advanceAfter, d1 + d2);
            } else {
                finishDecision(state, room, advanceAfter);
            }

            broadcast(code, state);
            return state;
        }
    }

    private void movePlayer(GameState state, Room room, String playerId, int steps) {
        if (state.isEnded()) return;
        int old = state.getPositions().get(playerId);
        int next = (old + steps) % BOARD_SIZE;
        if (next < old) {
            state.getBalances().merge(playerId, PASS_GO_BONUS, Integer::sum);
        }
        if (next == GO_TO_JAIL_POSITION) {
            sendToJail(state, room, playerId);
        } else {
            state.getPositions().put(playerId, next);
        }
    }

    private void sendToJail(GameState state, Room room, String playerId) {
        state.getPositions().put(playerId, JAIL_POSITION);
        state.getInJail().put(playerId, true);
        logEvent(state, playerName(room, playerId) + " راح عالمسكوبية 🔒");
    }

    private void advanceTurn(GameState state, Room room) {
        int size = state.getTurnOrder().size();
        int next = state.getCurrentTurnIndex();
        for (int i = 0; i < size; i++) {
            next = (next + 1) % size;
            String candidateId = state.getTurnOrder().get(next);
            boolean connected = room.getPlayers().stream()
                    .filter(p -> p.getId().equals(candidateId))
                    .findFirst().map(Player::isConnected).orElse(false);
            if (!connected) continue;
            if (Boolean.TRUE.equals(state.getSkipNextTurn().get(candidateId))) {
                state.getSkipNextTurn().put(candidateId, false);
                continue; // بوابة مغلقة - يتخطى دوره هاد بس
            }
            break;
        }
        state.setCurrentTurnIndex(next);
        state.setConsecutiveDoubles(0);
        state.setHasRolledThisTurn(false);
        state.setNegotiationUsedThisTurn(false);
        state.setTurnDeadline(Instant.now().plusSeconds(TURN_SECONDS));
    }

    private void finishDecision(GameState state, Room room, boolean advanceAfter) {
        if (advanceAfter) {
            advanceTurn(state, room);
        } else {
            state.setTurnDeadline(Instant.now().plusSeconds(TURN_SECONDS));
        }
    }

    // ---------------- الوقوف على خانة: يوزع حسب نوعها ----------------

    /** يحسم نتيجة الوقوف على الخانة الحالية: شراء/إيجار/ضريبة/بطاقة، وبيقرر بالنهاية تمرير الدور. */
    private void resolveLandingConsequences(GameState state, Room room, String playerId, boolean advanceAfter, int diceSum) {
        if (state.isEnded()) return;

        int pos = state.getPositions().get(playerId);
        BoardSquare sq = BoardData.SQUARES.get(pos);

        switch (sq.type()) {
            case PROPERTY, UTILITY -> {
                boolean opensPurchase = maybeOpenPurchaseDecision(state, playerId, advanceAfter);
                if (!opensPurchase) {
                    boolean paid = maybeChargeRent(state, room, playerId, diceSum, advanceAfter);
                    if (paid) finishDecision(state, room, advanceAfter);
                }
            }
            case TAX -> {
                boolean paid = chargeOrGoIntoDebt(state, playerId, sq.price(), null, advanceAfter);
                if (paid) logEvent(state, playerName(room, playerId) + " دفع ₪" + sq.price() + " ضريبة");
                if (paid) finishDecision(state, room, advanceAfter);
            }
            case CHANCE -> resolveCardDraw(state, room, playerId, CardDeckType.CHANCE, advanceAfter, diceSum);
            case COMMUNITY_CHEST -> resolveCardDraw(state, room, playerId, CardDeckType.COMMUNITY_CHEST, advanceAfter, diceSum);
            case JERUSALEM_GATE -> resolveCardDraw(state, room, playerId, CardDeckType.GATE, advanceAfter, diceSum);
            default -> finishDecision(state, room, advanceAfter);
        }
    }

    // ---------------- البطاقات: فرصة / صندوق الجماعة / بوابات القدس ----------------

    private static final Map<CardDeckType, String> DECK_LABELS = Map.of(
            CardDeckType.CHANCE, "فرصة",
            CardDeckType.COMMUNITY_CHEST, "صندوق الجماعة",
            CardDeckType.GATE, "بوابة"
    );

    private void resolveCardDraw(GameState state, Room room, String playerId, CardDeckType deckType,
                                 boolean advanceAfter, int diceSum) {
        Card card = drawCard(state, deckType);
        state.setLastCard(card.text(), deckType);
        logEvent(state, playerName(room, playerId) + " سحب " + DECK_LABELS.get(deckType) + ": " + card.text());
        applyCardEffect(state, room, playerId, card, advanceAfter, diceSum);
    }

    private Card drawCard(GameState state, CardDeckType deckType) {
        List<Card> deck = deckFor(state, deckType);
        List<Card> discard = discardFor(state, deckType);
        if (deck.isEmpty()) {
            deck.addAll(discard);
            discard.clear();
            Collections.shuffle(deck);
        }
        Card card = deck.remove(deck.size() - 1);
        discard.add(card);
        return card;
    }

    private List<Card> deckFor(GameState state, CardDeckType t) {
        return switch (t) {
            case CHANCE -> state.getChanceDeck();
            case COMMUNITY_CHEST -> state.getChestDeck();
            case GATE -> state.getGateDeck();
        };
    }

    private List<Card> discardFor(GameState state, CardDeckType t) {
        return switch (t) {
            case CHANCE -> state.getChanceDiscard();
            case COMMUNITY_CHEST -> state.getChestDiscard();
            case GATE -> state.getGateDiscard();
        };
    }

    private void applyCardEffect(GameState state, Room room, String playerId, Card card,
                                 boolean advanceAfter, int diceSum) {
        switch (card.type()) {
            case PAY -> {
                boolean paid = chargeOrGoIntoDebt(state, playerId, card.amount(), null, advanceAfter);
                if (paid) finishDecision(state, room, advanceAfter);
            }
            case COLLECT -> {
                state.getBalances().merge(playerId, card.amount(), Integer::sum);
                finishDecision(state, room, advanceAfter);
            }
            case COLLECT_FROM_ALL -> {
                for (Player p : room.getPlayers()) {
                    if (p.isConnected() && !p.getId().equals(playerId)) {
                        state.getBalances().merge(p.getId(), -card.amount(), Integer::sum);
                        state.getBalances().merge(playerId, card.amount(), Integer::sum);
                    }
                }
                finishDecision(state, room, advanceAfter);
            }
            case PAY_PER_BUILDING -> {
                int total = 0;
                for (Map.Entry<Integer, String> entry : state.getOwnership().entrySet()) {
                    if (!playerId.equals(entry.getValue())) continue;
                    int lvl = state.getHouses().getOrDefault(entry.getKey(), 0);
                    total += (lvl == 5) ? card.perHotel() : lvl * card.perHouse();
                }
                boolean paid = chargeOrGoIntoDebt(state, playerId, total, null, advanceAfter);
                if (paid) finishDecision(state, room, advanceAfter);
            }
            case JAIL_FREE_CARD -> {
                state.getJailFreeCards().merge(playerId, 1, Integer::sum);
                finishDecision(state, room, advanceAfter);
            }
            case GO_TO_JAIL -> {
                sendToJail(state, room, playerId);
                finishDecision(state, room, true);
            }
            case GATE_CLOSED -> {
                state.getSkipNextTurn().put(playerId, true);
                finishDecision(state, room, advanceAfter);
            }
            case MOVE_TO, MOVE_RELATIVE -> {
                // منوقف هون: نخلي اللاعب يبين لسا واقف عالبطاقة (مع نصها)
                // بهاد البث، وبعد فترة قصيرة الفحص الدوري بينفذ الحركة
                // الفعلية وبيبث تاني - حتى القطعة توصل فعلًا للبطاقة أول
                // بدل ما تقفز مباشرة لنتيجة الحركة الإضافية.
                state.setPendingCardMove(new PendingCardMove(playerId, card, diceSum, advanceAfter,
                        Instant.now().plusSeconds(CARD_MOVE_DELAY_SECONDS)));
            }
            case DRAW_CHANCE -> resolveCardDraw(state, room, playerId, CardDeckType.CHANCE, advanceAfter, diceSum);
            case DRAW_CHEST -> resolveCardDraw(state, room, playerId, CardDeckType.COMMUNITY_CHEST, advanceAfter, diceSum);
        }
    }

    private void resolvePendingCardMove(GameState state, Room room) {
        PendingCardMove pcm = state.getPendingCardMove();
        state.setPendingCardMove(null);
        Card card = pcm.getCard();
        String playerId = pcm.getPlayerId();

        if (card.type() == CardEffectType.MOVE_TO) {
            moveToPosition(state, room, playerId, card.targetPosition());
        } else {
            moveRelative(state, room, playerId, card.steps());
        }
        resolveLandingConsequences(state, room, playerId, pcm.isAdvanceAfter(), pcm.getDiceSum());
    }

    private void moveToPosition(GameState state, Room room, String playerId, int target) {
        if (state.isEnded()) return;
        int old = state.getPositions().get(playerId);
        if (target < old) {
            state.getBalances().merge(playerId, PASS_GO_BONUS, Integer::sum);
        }
        if (target == GO_TO_JAIL_POSITION) {
            sendToJail(state, room, playerId);
        } else {
            state.getPositions().put(playerId, target);
        }
    }

    private void moveRelative(GameState state, Room room, String playerId, int steps) {
        if (state.isEnded()) return;
        int old = state.getPositions().get(playerId);
        int next = ((old + steps) % BOARD_SIZE + BOARD_SIZE) % BOARD_SIZE;
        if (steps > 0 && next < old) {
            state.getBalances().merge(playerId, PASS_GO_BONUS, Integer::sum);
        }
        if (next == GO_TO_JAIL_POSITION) {
            sendToJail(state, room, playerId);
        } else {
            state.getPositions().put(playerId, next);
        }
    }

    // ---------------- المسكوبية: دفع الكفالة أو استعمال بطاقة شحرور ----------------

    public GameState payBail(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (!state.currentPlayerId().equals(playerId)) throw bad("مش دورك هلأ");
            if (!Boolean.TRUE.equals(state.getInJail().get(playerId))) throw bad("مش بالمسكوبية أصلًا");
            Integer balance = state.getBalances().get(playerId);
            if (balance == null || balance < 50) throw bad("رصيدك ما يكفي تدفع الكفالة");

            state.getBalances().merge(playerId, -50, Integer::sum);
            state.getInJail().put(playerId, false);
            state.getJailAttempts().put(playerId, 0);
            logEvent(state, playerName(room, playerId) + " دفع ₪50 وطلع من المسكوبية");

            broadcast(code, state);
            return state;
        }
    }

    public GameState useJailFreeCard(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (!state.currentPlayerId().equals(playerId)) throw bad("مش دورك هلأ");
            if (!Boolean.TRUE.equals(state.getInJail().get(playerId))) throw bad("مش بالمسكوبية أصلًا");
            int cards = state.getJailFreeCards().getOrDefault(playerId, 0);
            if (cards <= 0) throw bad("ما معك بطاقة شحرور");

            state.getJailFreeCards().put(playerId, cards - 1);
            state.getInJail().put(playerId, false);
            state.getJailAttempts().put(playerId, 0);
            logEvent(state, playerName(room, playerId) + " استخدم بطاقة شحرور وطلع من المسكوبية");

            broadcast(code, state);
            return state;
        }
    }

    private boolean maybeOpenPurchaseDecision(GameState state, String playerId, boolean advanceAfter) {
        if (state.isEnded()) return false;
        int pos = state.getPositions().get(playerId);
        BoardSquare sq = BoardData.SQUARES.get(pos);
        boolean purchasable = sq.type() == SquareType.PROPERTY || sq.type() == SquareType.UTILITY;
        if (!purchasable || state.getOwnership().containsKey(pos)) return false;

        state.setPendingPurchase(new PendingPurchase(pos, playerId, advanceAfter,
                Instant.now().plusSeconds(PURCHASE_DECISION_SECONDS)));
        return true;
    }

    public GameState buyPending(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            PendingPurchase pp = state.getPendingPurchase();
            if (pp == null || !pp.getPlayerId().equals(playerId)) throw bad("ما في قرار شراء إلك هلأ");

            BoardSquare sq = BoardData.SQUARES.get(pp.getPosition());
            int price = sq.price();
            if (state.getBalances().get(playerId) < price) throw bad("رصيدك ما يكفي تشتري بالسعر الكامل");

            state.getBalances().merge(playerId, -price, Integer::sum);
            state.getOwnership().put(pp.getPosition(), playerId);
            logEvent(state, playerName(room, playerId) + " اشترى " + sq.name() + " بـ₪" + price);
            boolean advanceAfter = pp.isAdvanceTurnAfter();
            state.setPendingPurchase(null);
            finishDecision(state, room, advanceAfter);

            broadcast(code, state);
            return state;
        }
    }

    public GameState openAuction(String code, String playerId) {
        roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            PendingPurchase pp = state.getPendingPurchase();
            if (pp == null || !pp.getPlayerId().equals(playerId)) throw bad("ما في قرار شراء إلك هلأ");

            BoardSquare sq = BoardData.SQUARES.get(pp.getPosition());
            int opening = (int) Math.ceil(sq.price() * 0.5);

            state.setAuction(new AuctionState(pp.getPosition(), playerId, opening, pp.isAdvanceTurnAfter(),
                    Instant.now().plusSeconds(AUCTION_SECONDS)));
            state.setPendingPurchase(null);

            broadcast(code, state);
            return state;
        }
    }

    public GameState bid(String code, String playerId, int amount) {
        roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            AuctionState auction = state.getAuction();
            if (auction == null) throw bad("ما في مزاد شغال هلأ");
            if (auction.getOpenerPlayerId().equals(playerId)) throw bad("فاتح المزاد ما بيقدر يزاود");

            int base = auction.getCurrentBidderId() == null ? auction.getOpeningPrice() : auction.getCurrentBid();
            int minBid = base + MIN_BID_INCREMENT;
            if (amount < minBid) throw bad("أقل مزايدة مسموحة ₪" + minBid);

            Integer balance = state.getBalances().get(playerId);
            if (balance == null || amount > balance) throw bad("ما تقدر تزاود أكتر من رصيدك");

            auction.setCurrentBid(amount);
            auction.setCurrentBidderId(playerId);
            auction.setDeadline(Instant.now().plusSeconds(AUCTION_SECONDS));

            broadcast(code, state);
            return state;
        }
    }

    private void resolveAuctionEnd(GameState state, Room room) {
        AuctionState auction = state.getAuction();
        boolean advanceAfter = auction.isAdvanceTurnAfter();
        BoardSquare sq = BoardData.SQUARES.get(auction.getPosition());
        if (auction.getCurrentBidderId() != null) {
            state.getBalances().merge(auction.getCurrentBidderId(), -auction.getCurrentBid(), Integer::sum);
            state.getOwnership().put(auction.getPosition(), auction.getCurrentBidderId());
            logEvent(state, playerName(room, auction.getCurrentBidderId()) + " فاز بمزاد "
                    + sq.name() + " بـ₪" + auction.getCurrentBid());
            state.setAuction(null);
            finishDecision(state, room, advanceAfter);
        } else {
            // فاتح المزاد ملزم يشتري - الملكية تنتقل فورًا، الدفع ممكن يتعلق كدين للبنك
            state.getOwnership().put(auction.getPosition(), auction.getOpenerPlayerId());
            logEvent(state, playerName(room, auction.getOpenerPlayerId()) + " اضطر يشتري "
                    + sq.name() + " بسعر الافتتاح ₪" + auction.getOpeningPrice());
            state.setAuction(null);
            boolean paid = chargeOrGoIntoDebt(state, auction.getOpenerPlayerId(), auction.getOpeningPrice(), null, advanceAfter);
            if (paid) finishDecision(state, room, advanceAfter);
        }
    }

    // ---------------- الإيجار ----------------

    /** يفرض الإيجار لو وقف اللاعب على أرض/مرفق مملوك لغيره وغير مرهون. يرجع true لو انسدد فورًا (أو ما كان مستحق أصلًا). */
    private boolean maybeChargeRent(GameState state, Room room, String playerId, int diceSum, boolean advanceAfter) {
        int pos = state.getPositions().get(playerId);
        BoardSquare sq = BoardData.SQUARES.get(pos);
        if (sq.type() != SquareType.PROPERTY && sq.type() != SquareType.UTILITY) return true;

        String owner = state.getOwnership().get(pos);
        if (owner == null || owner.equals(playerId)) return true;
        if (Boolean.TRUE.equals(state.getMortgaged().get(pos))) return true;

        int rent = computeRent(state, pos, diceSum);
        boolean paid = chargeOrGoIntoDebt(state, playerId, rent, owner, advanceAfter);
        if (paid) {
            logEvent(state, playerName(room, playerId) + " دفع ₪" + rent + " إيجار " + sq.name() + " لـ" + playerName(room, owner));
        }
        return paid;
    }

    /**
     * يحاول يخصم amount من playerId. لو رصيده يكفي، يخصم فورًا (ولو
     * فيه creditorId يضيفهم إله) ويرجع true. لو ما يكفي، يفتح "دين
     * معلق" بدل ما يخصم شي، ويرجع false - المستدعي ما لازم يمرر
     * الدور بهاي الحالة؛ الدور بيضل معلّق لحد ما ينحل الدين (سداد أو إفلاس).
     */
    private boolean chargeOrGoIntoDebt(GameState state, String playerId, int amount, String creditorId, boolean advanceAfter) {
        if (amount <= 0) return true;
        Integer balance = state.getBalances().get(playerId);
        if (balance != null && balance >= amount) {
            state.getBalances().merge(playerId, -amount, Integer::sum);
            if (creditorId != null) state.getBalances().merge(creditorId, amount, Integer::sum);
            return true;
        }
        state.setPendingDebt(new PendingDebt(playerId, creditorId, amount, advanceAfter,
                Instant.now().plusSeconds(DEBT_SECONDS)));
        return false;
    }

    private int computeRent(GameState state, int position, int diceSum) {
        BoardSquare sq = BoardData.SQUARES.get(position);
        String owner = state.getOwnership().get(position);

        if (sq.type() == SquareType.UTILITY) {
            long utilitiesOwned = BoardData.SQUARES.stream()
                    .filter(s -> s.type() == SquareType.UTILITY)
                    .filter(s -> owner.equals(state.getOwnership().get(s.position())))
                    .count();
            return diceSum * (utilitiesOwned >= 2 ? 10 : 4);
        }

        int houseLevel = state.getHouses().getOrDefault(position, 0);
        int[] rentTable = sq.rentTable();
        if (houseLevel > 0) return rentTable[houseLevel];

        boolean ownsFullGroup = ownsEntireGroup(state, position);
        return ownsFullGroup ? rentTable[0] * 2 : rentTable[0];
    }

    private boolean ownsEntireGroup(GameState state, int position) {
        BoardSquare sq = BoardData.SQUARES.get(position);
        ColorGroup group = sq.colorGroup();
        String owner = state.getOwnership().get(position);
        return BoardData.SQUARES.stream()
                .filter(s -> s.colorGroup() == group)
                .allMatch(s -> owner.equals(state.getOwnership().get(s.position())));
    }

    private boolean groupHasMortgaged(GameState state, ColorGroup group) {
        return BoardData.SQUARES.stream()
                .filter(s -> s.colorGroup() == group)
                .anyMatch(s -> Boolean.TRUE.equals(state.getMortgaged().get(s.position())));
    }

    private void validateOwnsProperty(GameState state, String playerId, int position) {
        if (!playerId.equals(state.getOwnership().get(position))) throw bad("هاي الأرض مش إلك");
    }

    // ---------------- البناء والهدم ----------------

    public GameState buildHouse(String code, String playerId, int position) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            validateOwnsProperty(state, playerId, position);
            BoardSquare sq = BoardData.SQUARES.get(position);
            if (sq.type() != SquareType.PROPERTY) throw bad("بس الأراضي قابلة للبناء عليها، مش المرافق");
            if (Boolean.TRUE.equals(state.getMortgaged().get(position))) throw bad("الأرض مرهونة، لازم تفك رهنها أول");
            if (!ownsEntireGroup(state, position)) throw bad("لازم تملك كل أراضي هاي المجموعة حتى تبني");
            if (groupHasMortgaged(state, sq.colorGroup())) throw bad("في أرض مرهونة بهاي المجموعة، لازم تفكها أول");

            int current = state.getHouses().getOrDefault(position, 0);
            if (current >= 5) throw bad("هاي الأرض عندها عمارة أصلًا، ما في أكتر تبني");
            if (!canBuildEvenly(state, sq.colorGroup(), position, current)) {
                throw bad("لازم تبني بالتساوي - ابنِ أول على الأرض يلي فيها بناء أقل بهاي المجموعة");
            }

            int cost = sq.colorGroup().getHouseCost();
            Integer balance = state.getBalances().get(playerId);
            if (balance == null || balance < cost) throw bad("رصيدك ما يكفي للبناء");

            state.getBalances().merge(playerId, -cost, Integer::sum);
            state.getHouses().put(position, current + 1);
            logEvent(state, playerName(room, playerId) + " بنى دار على " + sq.name());

            broadcast(code, state);
            return state;
        }
    }

    private boolean canBuildEvenly(GameState state, ColorGroup group, int position, int currentHouses) {
        int minInGroup = BoardData.SQUARES.stream()
                .filter(s -> s.colorGroup() == group)
                .mapToInt(s -> state.getHouses().getOrDefault(s.position(), 0))
                .min().orElse(0);
        return currentHouses <= minInGroup;
    }

    public GameState demolishHouse(String code, String playerId, int position) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            validateOwnsProperty(state, playerId, position);
            BoardSquare sq = BoardData.SQUARES.get(position);
            int current = state.getHouses().getOrDefault(position, 0);
            if (current <= 0) throw bad("ما في بناء تهدمه بهاي الأرض");

            int maxInGroup = BoardData.SQUARES.stream()
                    .filter(s -> s.colorGroup() == sq.colorGroup())
                    .mapToInt(s -> state.getHouses().getOrDefault(s.position(), 0))
                    .max().orElse(0);
            if (current < maxInGroup) throw bad("لازم تهدم من الأرض يلي فيها بناء أكتر بهاي المجموعة أول");

            int refund = sq.colorGroup().getHouseCost() / 2;
            state.getHouses().put(position, current - 1);
            state.getBalances().merge(playerId, refund, Integer::sum);
            logEvent(state, playerName(room, playerId) + " هدم دار من " + sq.name() + " (+₪" + refund + ")");

            broadcast(code, state);
            return state;
        }
    }

    // ---------------- الرهن وفك الرهن ----------------

    public GameState mortgage(String code, String playerId, int position) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            validateOwnsProperty(state, playerId, position);
            if (Boolean.TRUE.equals(state.getMortgaged().get(position))) throw bad("الأرض مرهونة أصلًا");
            BoardSquare sq = BoardData.SQUARES.get(position);
            if (groupHasAnyHouses(state, sq.colorGroup())) {
                throw bad("في بناء على أرض بهاي المجموعة، لازم تهدم كل بناء المجموعة أول");
            }

            int value = sq.price() / 2;
            state.getMortgaged().put(position, true);
            state.getBalances().merge(playerId, value, Integer::sum);
            logEvent(state, playerName(room, playerId) + " رهن " + sq.name() + " (+₪" + value + ")");

            broadcast(code, state);
            return state;
        }
    }

    private boolean groupHasAnyHouses(GameState state, ColorGroup group) {
        return BoardData.SQUARES.stream()
                .filter(s -> s.colorGroup() == group)
                .anyMatch(s -> state.getHouses().getOrDefault(s.position(), 0) > 0);
    }

    public GameState unmortgage(String code, String playerId, int position) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            validateOwnsProperty(state, playerId, position);
            if (!Boolean.TRUE.equals(state.getMortgaged().get(position))) throw bad("الأرض مش مرهونة");

            BoardSquare sq = BoardData.SQUARES.get(position);
            int cost = (int) Math.ceil(sq.price() / 2.0 * 1.1);
            Integer balance = state.getBalances().get(playerId);
            if (balance == null || balance < cost) throw bad("رصيدك ما يكفي تفك الرهن");

            state.getBalances().merge(playerId, -cost, Integer::sum);
            state.getMortgaged().put(position, false);
            logEvent(state, playerName(room, playerId) + " فك رهن " + sq.name());

            broadcast(code, state);
            return state;
        }
    }

    // ---------------- الإفلاس ----------------

    public GameState payDebt(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            PendingDebt debt = state.getPendingDebt();
            if (debt == null || !debt.getPlayerId().equals(playerId)) throw bad("ما في دين معلق إلك هلأ");

            Integer balance = state.getBalances().get(playerId);
            if (balance == null || balance < debt.getAmountOwed()) {
                throw bad("رصيدك لسا ما يكفي - ارهن أرض أو اهدم دار حتى يكفي المبلغ");
            }

            state.getBalances().merge(playerId, -debt.getAmountOwed(), Integer::sum);
            if (debt.getCreditorId() != null) {
                state.getBalances().merge(debt.getCreditorId(), debt.getAmountOwed(), Integer::sum);
            }
            logEvent(state, playerName(room, playerId) + " سدد دين ₪" + debt.getAmountOwed());
            boolean advanceAfter = debt.isAdvanceTurnAfter();
            state.setPendingDebt(null);
            finishDecision(state, room, advanceAfter);

            broadcast(code, state);
            return state;
        }
    }

    public GameState declareBankruptcy(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            PendingDebt debt = state.getPendingDebt();
            if (debt == null || !debt.getPlayerId().equals(playerId)) throw bad("ما في دين معلق إلك هلأ");

            executeBankruptcy(state, playerId, debt.getCreditorId());
            logEvent(state, playerName(room, playerId) + " أعلن إفلاسه وطلع من اللعبة 💔");
            state.setPendingDebt(null);
            removePlayerFromTurnOrder(state, room, playerId);

            broadcast(code, state);
            return state;
        }
    }

    /**
     * تنفيذ الإفلاس: نهدم كل بناء عليه أول (نص القيمة إله)، وبعدين
     * الأملاك كلها: للدائن بحالة مرهونة لو الدين للاعب، أو للبنك
     * فاضية بدون رهن لو الدين للبنك (القسم 10).
     */
    private void executeBankruptcy(GameState state, String bankruptPlayerId, String creditorId) {
        for (Map.Entry<Integer, String> e : new ArrayList<>(state.getOwnership().entrySet())) {
            if (!bankruptPlayerId.equals(e.getValue())) continue;
            int pos = e.getKey();
            int houses = state.getHouses().getOrDefault(pos, 0);
            if (houses <= 0) continue;
            BoardSquare sq = BoardData.SQUARES.get(pos);
            int refund = houses * (sq.colorGroup().getHouseCost() / 2);
            state.getBalances().merge(bankruptPlayerId, refund, Integer::sum);
            state.getHouses().put(pos, 0);
        }

        if (creditorId != null) {
            for (Map.Entry<Integer, String> e : new ArrayList<>(state.getOwnership().entrySet())) {
                if (!bankruptPlayerId.equals(e.getValue())) continue;
                int pos = e.getKey();
                state.getOwnership().put(pos, creditorId);
                state.getMortgaged().put(pos, true);
            }
            int remainingCash = Math.max(0, state.getBalances().getOrDefault(bankruptPlayerId, 0));
            state.getBalances().merge(creditorId, remainingCash, Integer::sum);
        } else {
            for (Map.Entry<Integer, String> e : new ArrayList<>(state.getOwnership().entrySet())) {
                if (!bankruptPlayerId.equals(e.getValue())) continue;
                int pos = e.getKey();
                state.getOwnership().remove(pos);
                state.getMortgaged().remove(pos);
            }
        }
        state.getBalances().put(bankruptPlayerId, 0);
    }

    /** يشيل اللاعب المفلس من ترتيب الأدوار. لو ضل لاعب واحد بس، هو الفائز مباشرة. */
    private void removePlayerFromTurnOrder(GameState state, Room room, String bankruptPlayerId) {
        List<String> order = state.getTurnOrder();
        int idx = order.indexOf(bankruptPlayerId);
        if (idx == -1) return;
        order.remove(idx);

        if (order.size() <= 1) {
            state.setEnded(true);
            if (order.size() == 1) {
                logEvent(state, playerName(room, order.get(0)) + " ربح! كل اللاعبين الباقيين أفلسوا 🏆");
            }
            return;
        }
        if (state.getCurrentTurnIndex() >= order.size()) {
            state.setCurrentTurnIndex(0);
        }
        // idx == currentTurnIndex دايمًا هون (الإفلاس بيصير بدور اللاعب
        // نفسه بس)، فبعد الحذف نفس الفهرس صار يشاور تلقائيًا على التالي.
        String candidateId = order.get(state.getCurrentTurnIndex());
        boolean connected = room.getPlayers().stream()
                .filter(p -> p.getId().equals(candidateId))
                .findFirst().map(Player::isConnected).orElse(false);
        if (connected && !Boolean.TRUE.equals(state.getSkipNextTurn().get(candidateId))) {
            state.setConsecutiveDoubles(0);
            state.setHasRolledThisTurn(false);
            state.setNegotiationUsedThisTurn(false);
            state.setTurnDeadline(Instant.now().plusSeconds(TURN_SECONDS));
        } else {
            int temp = state.getCurrentTurnIndex();
            state.setCurrentTurnIndex((temp - 1 + order.size()) % order.size());
            advanceTurn(state, room);
        }
    }

    /** لو ما تسدد الدين بالوقت: هدم كل شي ممكن هدمه، بعدين رهن كل شي ممكن رهنه، وإلا إفلاس إجباري (القسم 6). */
    private void resolveDebtAutomatically(GameState state, Room room) {
        PendingDebt debt = state.getPendingDebt();
        String playerId = debt.getPlayerId();

        autoRaiseFundsByDemolishing(state, playerId, debt.getAmountOwed());
        autoRaiseFundsByMortgaging(state, playerId, debt.getAmountOwed());

        Integer balance = state.getBalances().get(playerId);
        if (balance != null && balance >= debt.getAmountOwed()) {
            state.getBalances().merge(playerId, -debt.getAmountOwed(), Integer::sum);
            if (debt.getCreditorId() != null) {
                state.getBalances().merge(debt.getCreditorId(), debt.getAmountOwed(), Integer::sum);
            }
            logEvent(state, playerName(room, playerId) + " سدد دين ₪" + debt.getAmountOwed() + " تلقائيًا (هدم/رهن بدون قرار بالوقت)");
            boolean advanceAfter = debt.isAdvanceTurnAfter();
            state.setPendingDebt(null);
            finishDecision(state, room, advanceAfter);
        } else {
            executeBankruptcy(state, playerId, debt.getCreditorId());
            logEvent(state, playerName(room, playerId) + " أعلن إفلاسه تلقائيًا (ما قرر بالوقت) وطلع من اللعبة 💔");
            state.setPendingDebt(null);
            removePlayerFromTurnOrder(state, room, playerId);
        }
    }

    private void autoRaiseFundsByDemolishing(GameState state, String playerId, int needed) {
        boolean progress = true;
        while (progress && state.getBalances().getOrDefault(playerId, 0) < needed) {
            progress = false;
            for (Map.Entry<Integer, String> e : state.getOwnership().entrySet()) {
                if (!playerId.equals(e.getValue())) continue;
                int pos = e.getKey();
                int houses = state.getHouses().getOrDefault(pos, 0);
                if (houses <= 0) continue;
                BoardSquare sq = BoardData.SQUARES.get(pos);
                int maxInGroup = BoardData.SQUARES.stream()
                        .filter(s -> s.colorGroup() == sq.colorGroup())
                        .mapToInt(s -> state.getHouses().getOrDefault(s.position(), 0))
                        .max().orElse(0);
                if (houses < maxInGroup) continue;

                int refund = sq.colorGroup().getHouseCost() / 2;
                state.getHouses().put(pos, houses - 1);
                state.getBalances().merge(playerId, refund, Integer::sum);
                progress = true;
                if (state.getBalances().get(playerId) >= needed) return;
            }
        }
    }

    private void autoRaiseFundsByMortgaging(GameState state, String playerId, int needed) {
        for (Map.Entry<Integer, String> e : state.getOwnership().entrySet()) {
            if (state.getBalances().getOrDefault(playerId, 0) >= needed) return;
            if (!playerId.equals(e.getValue())) continue;
            int pos = e.getKey();
            if (Boolean.TRUE.equals(state.getMortgaged().get(pos))) continue;
            if (state.getHouses().getOrDefault(pos, 0) > 0) continue;
            BoardSquare sq = BoardData.SQUARES.get(pos);
            state.getMortgaged().put(pos, true);
            state.getBalances().merge(playerId, sq.price() / 2, Integer::sum);
        }
    }

    // ---------------- التفاوض والتداول ----------------

    private boolean allPurchasableSquaresOwned(GameState state) {
        return BoardData.SQUARES.stream()
                .filter(s -> s.type() == SquareType.PROPERTY || s.type() == SquareType.UTILITY)
                .allMatch(s -> state.getOwnership().containsKey(s.position()));
    }

    public GameState proposeTrade(String code, String playerId, String counterpartId,
                                  int offerCash, List<Integer> offerProperties,
                                  int requestCash, List<Integer> requestProperties) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (state.isEnded()) throw bad("اللعبة خلصت");
            if (!allPurchasableSquaresOwned(state)) {
                throw bad("التفاوض ما بيصير متاح إلا بعد ما تنباع كل الأراضي والمرافق");
            }
            if (!state.currentPlayerId().equals(playerId)) throw bad("بس صاحب الدور الحالي يقدر يفتح تفاوض");
            if (state.isHasRolledThisTurn()) throw bad("لازم تفتح التفاوض قبل ما ترمي النرد هاد الدور");
            if (state.isNegotiationUsedThisTurn()) throw bad("عرض تفاوض واحد بس مسموح بكل دور");
            if (state.getNegotiation() != null) throw bad("في عرض تفاوض شغال أصلًا");
            if (playerId.equals(counterpartId)) throw bad("ما تقدر تتفاوض مع نفسك");
            if (offerCash < 0 || requestCash < 0) throw bad("المبالغ لازم تكون صفر أو أكتر");

            boolean counterpartConnected = room.getPlayers().stream()
                    .anyMatch(p -> p.getId().equals(counterpartId) && p.isConnected());
            if (!counterpartConnected) throw bad("اللاعب يلي اخترته مش متصل");

            for (int pos : offerProperties) {
                if (!playerId.equals(state.getOwnership().get(pos))) throw bad("في أرض بعرضك مش إلك");
                if (state.getHouses().getOrDefault(pos, 0) > 0) throw bad("لازم تهدم كل بناء على أراضي عرضك قبل التفاوض");
            }
            for (int pos : requestProperties) {
                if (!counterpartId.equals(state.getOwnership().get(pos))) throw bad("في أرض بطلبك مش ملك الطرف التاني");
                if (state.getHouses().getOrDefault(pos, 0) > 0) throw bad("لازم يهدم الطرف التاني بناء أراضي طلبك قبل التفاوض");
            }

            state.setNegotiation(new NegotiationSession(playerId, counterpartId, offerCash,
                    List.copyOf(offerProperties), requestCash, List.copyOf(requestProperties),
                    Instant.now().plusSeconds(NEGOTIATION_SECONDS)));
            state.setNegotiationUsedThisTurn(true);

            broadcast(code, state);
            return state;
        }
    }

    public GameState respondTrade(String code, String playerId, boolean accept) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            NegotiationSession session = state.getNegotiation();
            if (session == null) throw bad("ما في عرض تفاوض شغال");
            if (!session.getCounterpartId().equals(playerId)) throw bad("هاد العرض مش إلك ترد عليه");

            state.setNegotiation(null); // نمسحه أول، حتى لو فشل التنفيذ ما يضل عالق
            RuntimeException failure = null;
            if (accept) {
                try {
                    executeTrade(state, room, session);
                } catch (RuntimeException e) {
                    failure = e; // منبث إلغاء العرض لأي حال، وبعدين نرجع نطلع الخطأ لصاحب الطلب
                }
            }

            broadcast(code, state);
            if (failure != null) throw failure;
            return state;
        }
    }

    public GameState cancelTrade(String code, String playerId) {
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            NegotiationSession session = state.getNegotiation();
            if (session == null) throw bad("ما في عرض تفاوض شغال");
            if (!session.getInitiatorId().equals(playerId)) throw bad("بس صاحب العرض يقدر يسحبه");
            state.setNegotiation(null);
            broadcast(code, state);
            return state;
        }
    }

    private void executeTrade(GameState state, Room room, NegotiationSession s) {
        String a = s.getInitiatorId();
        String b = s.getCounterpartId();

        Integer balA = state.getBalances().get(a);
        Integer balB = state.getBalances().get(b);
        if (balA == null || balA < s.getOfferCash()) throw bad("رصيد صاحب العرض ما عاد يكفي، الصفقة فشلت");
        if (balB == null || balB < s.getRequestCash()) throw bad("رصيدك ما عاد يكفي، الصفقة فشلت");

        for (int pos : s.getOfferProperties()) {
            if (!a.equals(state.getOwnership().get(pos))) throw bad("صاحب العرض ما عاد يملك إحدى أراضي العرض، الصفقة فشلت");
        }
        for (int pos : s.getRequestProperties()) {
            if (!b.equals(state.getOwnership().get(pos))) throw bad("ما عدت تملك إحدى الأراضي المطلوبة، الصفقة فشلت");
        }

        state.getBalances().merge(a, s.getRequestCash() - s.getOfferCash(), Integer::sum);
        state.getBalances().merge(b, s.getOfferCash() - s.getRequestCash(), Integer::sum);

        for (int pos : s.getOfferProperties()) state.getOwnership().put(pos, b);
        for (int pos : s.getRequestProperties()) state.getOwnership().put(pos, a);

        logEvent(state, playerName(room, a) + " وافق " + playerName(room, b) + " على صفقة تفاوض");
    }

    // ---------------- تصويت إنهاء اللعبة ----------------

    public GameState proposeEndVote(String code, String playerId) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (state.isEnded()) throw bad("اللعبة خلصت أصلًا");
            state.setVoteActive(true);
            state.getVoteResponses().clear();
            state.getVoteResponses().put(playerId, true);
            state.setVoteDeadline(Instant.now().plusSeconds(VOTE_SECONDS));
            checkVoteOutcome(state, room);
            broadcast(code, state);
            return state;
        }
    }

    public GameState respondEndVote(String code, String playerId, boolean approve) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (!state.isVoteActive()) throw bad("ما في تصويت شغال هلأ");
            if (!approve) {
                state.setVoteActive(false);
                state.getVoteResponses().clear();
            } else {
                state.getVoteResponses().put(playerId, true);
                checkVoteOutcome(state, room);
            }
            broadcast(code, state);
            return state;
        }
    }

    private void checkVoteOutcome(GameState state, Room room) {
        boolean allApproved = room.getPlayers().stream()
                .filter(Player::isConnected)
                .allMatch(p -> Boolean.TRUE.equals(state.getVoteResponses().get(p.getId())));
        if (allApproved) {
            state.setEnded(true);
            state.setVoteActive(false);
            logEvent(state, "اللاعبين وافقوا كلهم على إنهاء اللعبة");
        }
    }

    // ---------------- تصويت تمديد وقت المباراة (بعد 60 دقيقة) ----------------

    public GameState respondExtendVote(String code, String playerId, boolean approve) {
        Room room = roomService.getRoomOrThrow(code);
        GameState state = getStateOrThrow(code);
        synchronized (state) {
            if (!state.isExtendVoteActive()) throw bad("ما في تصويت تمديد شغال هلأ");
            if (!approve) {
                // أي رفض صريح لتمديد الوقت ينهي اللعبة فورًا
                state.setExtendVoteActive(false);
                state.getExtendVoteResponses().clear();
                state.setEnded(true);
                logEvent(state, playerName(room, playerId) + " رفض التمديد - اللعبة انتهت");
            } else {
                state.getExtendVoteResponses().put(playerId, true);
                checkExtendVoteOutcome(state, room);
            }
            broadcast(code, state);
            return state;
        }
    }

    private void checkExtendVoteOutcome(GameState state, Room room) {
        boolean allApproved = room.getPlayers().stream()
                .filter(Player::isConnected)
                .allMatch(p -> Boolean.TRUE.equals(state.getExtendVoteResponses().get(p.getId())));
        if (allApproved) {
            state.setExtendVoteActive(false);
            state.getExtendVoteResponses().clear();
            state.setMatchEndDeadline(Instant.now().plusSeconds(15L * 60));
            logEvent(state, "الكل وافق - انمدت اللعبة 15 دقيقة إضافية");
        }
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private String playerName(Room room, String playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .map(Player::getName)
                .orElse("لاعب");
    }

    /** يضيف سطر لسجل الأحداث المختصر، ويحافظ على آخر 40 حدث بس. */
    private void logEvent(GameState state, String message) {
        List<String> log = state.getEventLog();
        log.add(message);
        while (log.size() > 40) {
            log.remove(0);
        }
    }

    private void broadcast(String code, GameState state) {
        messaging.convertAndSend("/topic/games/" + code, GameStateResponse.from(state));
    }

    /** مهل الدور، وقرار الشراء، والمزاد، وتصويت الإنهاء - كلهم يمرون هون. */
    @Scheduled(fixedRate = 1000)
    public void sweepTimeouts() {
        Instant now = Instant.now();
        games.forEach((code, state) -> {
            try {
                Room room = roomService.getRoomOrThrow(code);
                synchronized (state) {
                    if (state.isEnded()) return;

                    if (state.getPendingPurchase() != null && now.isAfter(state.getPendingPurchase().getDeadline())) {
                        PendingPurchase pp = state.getPendingPurchase();
                        BoardSquare sq = BoardData.SQUARES.get(pp.getPosition());
                        int opening = (int) Math.ceil(sq.price() * 0.5);
                        state.setAuction(new AuctionState(pp.getPosition(), pp.getPlayerId(), opening,
                                pp.isAdvanceTurnAfter(), Instant.now().plusSeconds(AUCTION_SECONDS)));
                        state.setPendingPurchase(null);
                        broadcast(code, state);
                    } else if (state.getAuction() != null && now.isAfter(state.getAuction().getDeadline())) {
                        resolveAuctionEnd(state, room);
                        broadcast(code, state);
                    } else if (state.getPendingPurchase() == null && state.getAuction() == null
                            && state.getTurnDeadline() != null && now.isAfter(state.getTurnDeadline())) {
                        String playerId = state.currentPlayerId();
                        state.getMissedTurns().merge(playerId, 1, Integer::sum);
                        advanceTurn(state, room);
                        broadcast(code, state);
                    }

                    if (state.isVoteActive() && state.getVoteDeadline() != null && now.isAfter(state.getVoteDeadline())) {
                        state.setVoteActive(false);
                        state.getVoteResponses().clear();
                        broadcast(code, state);
                    }

                    if (state.getNegotiation() != null && now.isAfter(state.getNegotiation().getDeadline())) {
                        state.setNegotiation(null);
                        broadcast(code, state);
                    }

                    if (state.getPendingDebt() != null && now.isAfter(state.getPendingDebt().getDeadline())) {
                        resolveDebtAutomatically(state, room);
                        broadcast(code, state);
                    }

                    if (state.getPendingCardMove() != null && now.isAfter(state.getPendingCardMove().getResolveAt())) {
                        resolvePendingCardMove(state, room);
                        broadcast(code, state);
                    }

                    if (!state.isExtendVoteActive() && state.getMatchEndDeadline() != null
                            && now.isAfter(state.getMatchEndDeadline())) {
                        state.setExtendVoteActive(true);
                        state.getExtendVoteResponses().clear();
                        state.setExtendVoteDeadline(now.plusSeconds(VOTE_SECONDS));
                        broadcast(code, state);
                    } else if (state.isExtendVoteActive() && state.getExtendVoteDeadline() != null
                            && now.isAfter(state.getExtendVoteDeadline())) {
                        // ما اكتمل الإجماع بالوقت = اللعبة تنتهي (القسم 6)
                        state.setExtendVoteActive(false);
                        state.getExtendVoteResponses().clear();
                        state.setEnded(true);
                        logEvent(state, "ما اكتمل إجماع التمديد بالوقت - اللعبة انتهت");
                        broadcast(code, state);
                    }
                }
            } catch (Exception e) {
                // ما منوقف فحص باقي الغرف بسبب مشكلة بغرفة وحدة
            }
        });
    }
}