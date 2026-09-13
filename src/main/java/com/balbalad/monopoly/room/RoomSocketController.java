package com.balbalad.monopoly.room;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class RoomSocketController {

    public record RegisterPayload(String playerId) {}

    private final RoomSocketEventListener eventListener;

    public RoomSocketController(RoomSocketEventListener eventListener) {
        this.eventListener = eventListener;
    }

    /** العميل يرسل هون فورًا بعد الاتصال، حتى نربط sessionId بهوية اللاعب. */
    @MessageMapping("/rooms/{code}/register")
    public void register(@DestinationVariable String code, RegisterPayload payload,
                          SimpMessageHeaderAccessor headerAccessor) {
        eventListener.registerSession(headerAccessor.getSessionId(), code, payload.playerId());
    }
}
