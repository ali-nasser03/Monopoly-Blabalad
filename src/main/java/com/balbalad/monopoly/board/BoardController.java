package com.balbalad.monopoly.board;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * نقطة وحيدة لقراءة بيانات اللوح. board.js يتغذى من هون فقط،
 * حتى أي تعديل بـ BoardData.java ينعكس تلقائيًا بالواجهة بدون
 * أي تعديل إضافي بالفرونت-إند (القسم 2 من وثيقة المواصفات).
 */
@RestController
@RequestMapping("/api")
public class BoardController {

    @GetMapping("/board")
    public List<BoardSquare> getBoard() {
        return BoardData.SQUARES;
    }
}
