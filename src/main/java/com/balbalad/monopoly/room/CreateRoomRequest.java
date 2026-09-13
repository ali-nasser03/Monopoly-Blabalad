package com.balbalad.monopoly.room;

public record CreateRoomRequest(String name, Piece piece, int maxPlayers) {}
