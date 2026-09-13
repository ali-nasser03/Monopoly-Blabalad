package com.balbalad.monopoly.game;

import java.util.List;

public record TradeProposalRequest(
        String playerId,
        String counterpartId,
        int offerCash,
        List<Integer> offerProperties,
        int requestCash,
        List<Integer> requestProperties
) {}
