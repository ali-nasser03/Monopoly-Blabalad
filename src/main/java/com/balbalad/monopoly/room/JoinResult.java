package com.balbalad.monopoly.room;

/** نتيجة داخلية من RoomService: مين اللاعب يلي صار الفعل باسمه + الغرفة. */
record JoinResult(Player player, Room room) {}
