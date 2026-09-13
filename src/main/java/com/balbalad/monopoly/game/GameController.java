package com.balbalad.monopoly.game;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/{code}")
    public GameStateResponse state(@PathVariable String code) {
        return GameStateResponse.from(gameService.getStateOrThrow(code));
    }

    @PostMapping("/{code}/roll")
    public GameStateResponse roll(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.roll(code, req.playerId()));
    }

    @PostMapping("/{code}/purchase/buy")
    public GameStateResponse buy(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.buyPending(code, req.playerId()));
    }

    @PostMapping("/{code}/purchase/auction")
    public GameStateResponse openAuction(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.openAuction(code, req.playerId()));
    }

    @PostMapping("/{code}/jail/pay")
    public GameStateResponse payBail(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.payBail(code, req.playerId()));
    }

    @PostMapping("/{code}/jail/use-card")
    public GameStateResponse useJailFreeCard(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.useJailFreeCard(code, req.playerId()));
    }

    @PostMapping("/{code}/negotiation/propose")
    public GameStateResponse proposeTrade(@PathVariable String code, @RequestBody TradeProposalRequest req) {
        return GameStateResponse.from(gameService.proposeTrade(code, req.playerId(), req.counterpartId(),
                req.offerCash(), req.offerProperties(), req.requestCash(), req.requestProperties()));
    }

    @PostMapping("/{code}/negotiation/respond")
    public GameStateResponse respondTrade(@PathVariable String code, @RequestBody TradeResponseRequest req) {
        return GameStateResponse.from(gameService.respondTrade(code, req.playerId(), req.accept()));
    }

    @PostMapping("/{code}/negotiation/cancel")
    public GameStateResponse cancelTrade(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.cancelTrade(code, req.playerId()));
    }

    @PostMapping("/{code}/extend-vote/respond")
    public GameStateResponse respondExtendVote(@PathVariable String code, @RequestBody EndVoteResponseRequest req) {
        return GameStateResponse.from(gameService.respondExtendVote(code, req.playerId(), req.approve()));
    }

    @PostMapping("/{code}/debt/pay")
    public GameStateResponse payDebt(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.payDebt(code, req.playerId()));
    }

    @PostMapping("/{code}/debt/bankrupt")
    public GameStateResponse declareBankruptcy(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.declareBankruptcy(code, req.playerId()));
    }

    @PostMapping("/{code}/auction/bid")
    public GameStateResponse bid(@PathVariable String code, @RequestBody BidRequest req) {
        return GameStateResponse.from(gameService.bid(code, req.playerId(), req.amount()));
    }

    @PostMapping("/{code}/properties/{position}/build")
    public GameStateResponse build(@PathVariable String code, @PathVariable int position, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.buildHouse(code, req.playerId(), position));
    }

    @PostMapping("/{code}/properties/{position}/demolish")
    public GameStateResponse demolish(@PathVariable String code, @PathVariable int position, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.demolishHouse(code, req.playerId(), position));
    }

    @PostMapping("/{code}/properties/{position}/mortgage")
    public GameStateResponse mortgage(@PathVariable String code, @PathVariable int position, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.mortgage(code, req.playerId(), position));
    }

    @PostMapping("/{code}/properties/{position}/unmortgage")
    public GameStateResponse unmortgage(@PathVariable String code, @PathVariable int position, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.unmortgage(code, req.playerId(), position));
    }

    @GetMapping("/{code}/balance/{playerId}")
    public BalanceResponse balance(@PathVariable String code, @PathVariable String playerId) {
        GameState state = gameService.getStateOrThrow(code);
        Integer balance = state.getBalances().get(playerId);
        if (balance == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "لاعب غير موجود بهاي اللعبة");
        return new BalanceResponse(balance);
    }

    @PostMapping("/{code}/end-vote/start")
    public GameStateResponse startEndVote(@PathVariable String code, @RequestBody PlayerActionRequest req) {
        return GameStateResponse.from(gameService.proposeEndVote(code, req.playerId()));
    }

    @PostMapping("/{code}/end-vote/respond")
    public GameStateResponse respondEndVote(@PathVariable String code, @RequestBody EndVoteResponseRequest req) {
        return GameStateResponse.from(gameService.respondEndVote(code, req.playerId(), req.approve()));
    }
}
