package com.balbalad.monopoly.game;

/**
 * بطاقة واحدة من مجموعات فرصة/صندوق الجماعة/بوابات القدس.
 *
 * @param text           النص يلي يظهر للجميع لما تُسحب.
 * @param type           نوع المفعول.
 * @param amount         مبلغ ثابت (PAY/COLLECT/COLLECT_FROM_ALL).
 * @param targetPosition خانة الوجهة (MOVE_TO فقط).
 * @param steps          عدد الخطوات، سالب للخلف (MOVE_RELATIVE فقط).
 * @param perHouse       مبلغ لكل دار (PAY_PER_BUILDING فقط).
 * @param perHotel       مبلغ لكل عمارة (PAY_PER_BUILDING فقط).
 */
public record Card(
        String text,
        CardEffectType type,
        int amount,
        int targetPosition,
        int steps,
        int perHouse,
        int perHotel
) {}
