package com.balbalad.monopoly.board;

import java.util.List;

import static com.balbalad.monopoly.board.ColorGroup.*;
import static com.balbalad.monopoly.board.SquareType.*;

/**
 * مصدر الحقيقة الوحيد لبيانات اللوح - القسم 5، 8، 9 من وثيقة
 * المواصفات V1 (2 أيلول 2026).
 *
 * أي تعديل معتمد لاحقًا على اللوح (اسم، سعر، إيجار...) لازم يصير
 * هون فقط، وبينعكس تلقائيًا على الواجهة عبر GET /api/board، تماشيًا
 * مع القسم 2: "ملف اللوح مستقل عن واجهة اللعب؛ أي تعديل معتمد على
 * اللوح يجب أن يظهر تلقائيًا في واجهة اللعب."
 *
 * ترتيب القراءة: خانة 0 هي البداية أسفل اليمين، والحركة من اليمين
 * إلى اليسار حول اللوح (القسم 5).
 */
public final class BoardData {

    private BoardData() {}

    public static final List<BoardSquare> SQUARES = List.of(
            go(0, "البداية"),
            property(1, "أبو ديس", 60, BROWN, new int[]{2, 10, 30, 90, 160, 250}),
            card(2, "صندوق الجماعة", COMMUNITY_CHEST),
            property(3, "العيزرية", 60, BROWN, new int[]{4, 20, 60, 180, 320, 450}),
            tax(4, "ضريبة", 200),
            gate(5, "باب العمود"),
            property(6, "وادي الحمص", 100, LIGHT_BLUE, new int[]{6, 30, 90, 270, 400, 550}),
            card(7, "فرصة", CHANCE),
            property(8, "أم طوبا", 100, LIGHT_BLUE, new int[]{6, 30, 90, 270, 400, 550}),
            property(9, "صورباهر", 120, LIGHT_BLUE, new int[]{8, 40, 100, 300, 450, 600}),
            jail(10, "المسكوبية"),
            property(11, "ضاحية السلام", 140, PINK, new int[]{10, 50, 150, 450, 625, 750}),
            utility(12, "شركة كهرباء القدس JDECO", 140),
            property(13, "راس خميس", 140, PINK, new int[]{10, 50, 150, 450, 625, 750}),
            property(14, "راس شحادة", 160, PINK, new int[]{12, 60, 180, 500, 700, 900}),
            gate(15, "باب الخليل"),
            property(16, "الشيخ سعد", 180, ORANGE, new int[]{14, 70, 200, 550, 750, 950}),
            card(17, "صندوق الجماعة", COMMUNITY_CHEST),
            property(18, "الصلعة", 180, ORANGE, new int[]{14, 70, 200, 550, 750, 950}),
            property(19, "السواحرة", 200, ORANGE, new int[]{16, 80, 220, 600, 800, 1000}),
            freeParking(20, "المطل"),
            property(21, "ضاحية البريد", 220, RED, new int[]{18, 90, 250, 700, 875, 1050}),
            card(22, "فرصة", CHANCE),
            property(23, "كفر عقب", 220, RED, new int[]{18, 90, 250, 700, 875, 1050}),
            property(24, "بيت حنينا", 240, RED, new int[]{20, 100, 300, 750, 925, 1100}),
            gate(25, "باب الأسباط"),
            property(26, "راس العمود", 260, YELLOW, new int[]{22, 110, 330, 800, 975, 1150}),
            property(27, "عين اللوزة", 260, YELLOW, new int[]{22, 110, 330, 800, 975, 1150}),
            utility(28, "شركة المياه جيحون", 140),
            property(29, "وادي حلوة", 280, YELLOW, new int[]{24, 120, 360, 850, 1025, 1200}),
            goToJail(30, "استدعاء للمسكوبية"),
            property(31, "العيسوية", 300, GREEN, new int[]{26, 130, 390, 900, 1100, 1275}),
            property(32, "وادي الجوز", 300, GREEN, new int[]{26, 130, 390, 900, 1100, 1275}),
            card(33, "صندوق الجماعة", COMMUNITY_CHEST),
            property(34, "الشيخ جراح", 320, GREEN, new int[]{28, 150, 450, 1000, 1200, 1400}),
            gate(35, "باب الساهرة"),
            card(36, "فرصة", CHANCE),
            property(37, "الصوانة", 350, DARK_BLUE, new int[]{35, 175, 500, 1100, 1300, 1500}),
            tax(38, "ضريبة", 100),
            property(39, "الطور", 400, DARK_BLUE, new int[]{50, 200, 600, 1400, 1700, 2000})
    );

    private static BoardSquare go(int pos, String name) {
        return new BoardSquare(pos, name, GO, null, NONE, null);
    }

    private static BoardSquare property(int pos, String name, int price, ColorGroup group, int[] rents) {
        return new BoardSquare(pos, name, PROPERTY, price, group, rents);
    }

    private static BoardSquare utility(int pos, String name, int price) {
        return new BoardSquare(pos, name, UTILITY, price, NONE, null);
    }

    private static BoardSquare card(int pos, String name, SquareType type) {
        return new BoardSquare(pos, name, type, null, NONE, null);
    }

    private static BoardSquare tax(int pos, String name, int amount) {
        return new BoardSquare(pos, name, TAX, amount, NONE, null);
    }

    private static BoardSquare gate(int pos, String name) {
        return new BoardSquare(pos, name, JERUSALEM_GATE, null, NONE, null);
    }

    private static BoardSquare jail(int pos, String name) {
        return new BoardSquare(pos, name, JAIL, null, NONE, null);
    }

    private static BoardSquare freeParking(int pos, String name) {
        return new BoardSquare(pos, name, FREE_PARKING, null, NONE, null);
    }

    private static BoardSquare goToJail(int pos, String name) {
        return new BoardSquare(pos, name, GO_TO_JAIL, null, NONE, null);
    }
}
