package com.balbalad.monopoly.board;

/**
 * مجموعات ألوان الأراضي، وتكلفة الدار/الترقية لكل مجموعة (القسم 8
 * من وثيقة المواصفات). الأسماء هنا تصنيف داخلي فقط للباك-إند
 * (قواعد البناء والتوازن)؛ الألوان الفعلية على الشاشة (خشب/حجر/
 * زيتي/عنابي/ذهبي) قرار فرونت-إند بحت وما إلها علاقة بأسماء
 * الـ enum هون.
 */
public enum ColorGroup {
    NONE(0),
    BROWN(50),
    LIGHT_BLUE(50),
    PINK(100),
    ORANGE(100),
    RED(150),
    YELLOW(150),
    GREEN(200),
    DARK_BLUE(200);

    private final int houseCost;

    ColorGroup(int houseCost) {
        this.houseCost = houseCost;
    }

    /** تكلفة كل دار/ترقية بهاي المجموعة، بالشيكل. */
    public int getHouseCost() {
        return houseCost;
    }
}
