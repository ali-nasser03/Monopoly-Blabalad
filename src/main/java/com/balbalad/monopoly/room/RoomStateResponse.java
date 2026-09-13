package com.balbalad.monopoly.room;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record RoomStateResponse(
        String code,
        int maxPlayers,
        boolean started,
        List<PlayerResponse> players,
        List<Piece> availablePieces
) {
    static RoomStateResponse from(Room room, Set<Piece> availablePieces) {
        List<PlayerResponse> players = room.getPlayers().stream()
                .map(PlayerResponse::from)
                .collect(Collectors.toList());
        return new RoomStateResponse(room.getCode(), room.getMaxPlayers(), room.isStarted(), players, List.copyOf(availablePieces));
    }
}
