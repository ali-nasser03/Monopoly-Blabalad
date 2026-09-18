package com.balbalad.monopoly.room;

import com.balbalad.monopoly.game.GameService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;
    private final GameService gameService;

    public RoomController(RoomService roomService, GameService gameService) {
        this.roomService = roomService;
        this.gameService = gameService;
    }

    @PostMapping
    public JoinedRoomResponse create(@RequestBody CreateRoomRequest req) {
        JoinResult result = roomService.createRoom(req.name(), req.piece(), req.maxPlayers());
        return JoinedRoomResponse.of(result, roomService.availablePieces(result.room()));
    }

    /** نفس النقطة تُستعمل للدخول الأول وإعادة الاتصال (نفس الاسم والقطعة خلال 5 دقائق). */
    @PostMapping("/{code}/join")
    public JoinedRoomResponse join(@PathVariable String code, @RequestBody JoinRoomRequest req) {
        JoinResult result = roomService.joinRoom(code, req.name(), req.piece());
        return JoinedRoomResponse.of(result, roomService.availablePieces(result.room()));
    }

    @GetMapping("/{code}")
    public RoomStateResponse state(@PathVariable String code) {
        var room = roomService.getRoomOrThrow(code);
        return RoomStateResponse.from(room, roomService.availablePieces(room));
    }

    @PostMapping("/{code}/add-bot")
    public RoomStateResponse addBot(@PathVariable String code, @RequestBody StartGameRequest req) {
        var room = roomService.addBot(code, req.playerId());
        return RoomStateResponse.from(room, roomService.availablePieces(room));
    }

    @PostMapping("/{code}/start")
    public RoomStateResponse start(@PathVariable String code, @RequestBody StartGameRequest req) {
        var room = roomService.startGame(code, req.playerId());
        gameService.initGame(room);
        return RoomStateResponse.from(room, roomService.availablePieces(room));
    }
}