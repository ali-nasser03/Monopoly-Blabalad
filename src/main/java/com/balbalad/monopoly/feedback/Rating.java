package com.balbalad.monopoly.feedback;

public record Rating(String roomCode, int stars, String type, String text, String submittedAt) {}
