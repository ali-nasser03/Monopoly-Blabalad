package com.balbalad.monopoly.room;

public record PlayerResponse(String id, String name, Piece piece, boolean host, boolean connected, boolean bot) {

    static PlayerResponse from(Player p) {
        return new PlayerResponse(p.getId(), p.getName(), p.getPiece(), p.isHost(), p.isConnected(), p.isBot());
    }
}