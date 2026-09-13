package com.balbalad.monopoly.board;

/**
 * خانة واحدة على اللوح (40 خانة بالمجموع - القسم 5، 8، 9 بوثيقة
 * المواصفات V1).
 *
 * @param position   رقم الخانة، من 0 إلى 39.
 * @param name       الاسم يلي يظهر على اللوح وبالبطاقة.
 * @param type       نوع الخانة.
 * @param price      سعر الشراء (أرض/مرفق) أو مبلغ الضريبة (ضريبة)؛ null لغير ذلك.
 * @param colorGroup مجموعة اللون للأراضي فقط؛ NONE لأي نوع تاني.
 * @param rentTable  [بدون بناء, دار, داران, 3 دور, 4 دور, عمارة] للأراضي فقط؛ null لغير ذلك.
 */
public record BoardSquare(
        int position,
        String name,
        SquareType type,
        Integer price,
        ColorGroup colorGroup,
        int[] rentTable
) {
    /** قيمة الرهن = 50% من السعر الأصلي (القسم 10). صفر لغير القابل للشراء. */
    public int mortgageValue() {
        return price == null ? 0 : price / 2;
    }
}
