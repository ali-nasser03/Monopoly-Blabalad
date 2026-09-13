package com.balbalad.monopoly.feedback;

public record RatingRequest(String roomCode, int stars, String type, String text) {}
