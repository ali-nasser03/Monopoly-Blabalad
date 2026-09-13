package com.balbalad.monopoly.room;

import java.util.Set;

public record JoinedRoomResponse(String playerId, RoomStateResponse room) {

    static JoinedRoomResponse of(JoinResult result, Set<Piece> availablePieces) {
        return new JoinedRoomResponse(result.player().getId(), RoomStateResponse.from(result.room(), availablePieces));
    }
}
