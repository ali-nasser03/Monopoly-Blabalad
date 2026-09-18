package com.balbalad.monopoly.game;

import java.util.List;

import static com.balbalad.monopoly.game.CardEffectType.*;

/**
 * مصدر الحقيقة لنصوص ومفاعيل البطاقات - القسم 11 و12 من وثيقة
 * المواصفات V1. الخانات المرجعية (targetPosition) مطابقة لأرقام
 * BoardData.java: أبو ديس=1، ضاحية السلام=11، باب الخليل=15،
 * بيت حنينا=24، الطور=39، البداية=0.
 */
public final class CardData {

    private CardData() {}

    public static final List<Card> CHANCE = List.of(
            new Card("ادفع أرنونا: ₪50 عن كل دار و₪150 عن كل عمارة", PAY_PER_BUILDING, 0, -1, 0, 50, 150),
            new Card("ادفع ₪150 قسط جامعة", PAY, 150, -1, 0, 0, 0),
            new Card("ارجع 3 خطوات للخلف", MOVE_RELATIVE, 0, -1, -3, 0, 0),
            new Card("ادفع ₪50 ضريبة", PAY, 50, -1, 0, 0, 0),
            new Card("ربحت باللوتو: اقبض ₪50", COLLECT, 50, -1, 0, 0, 0),
            new Card("تقدم لخط البداية", MOVE_TO, 0, 0, 0, 0, 0),
            new Card("تقدم لبيت حنينا، واقبض ₪200 إذا مررت بالبداية", MOVE_TO, 0, 24, 0, 0, 0),
            new Card("استُدعيت للتحقيق بالمسكوبية", GO_TO_JAIL, 0, -1, 0, 0, 0),
            new Card("تقدم لضاحية السلام، واقبض ₪200 إذا مررت بالبداية", MOVE_TO, 0, 11, 0, 0, 0),
            new Card("تقدم لباب الخليل، واقبض ₪200 إذا مررت بالبداية", MOVE_TO, 0, 15, 0, 0, 0),
            new Card("اطلع للطور، واقبض ₪200 إذا مررت بالبداية", MOVE_TO, 0, 39, 0, 0, 0),
            new Card("بطاقة شحرور من المسكوبية - احتفظ فيها لحد ما تحتاجها", JAIL_FREE_CARD, 0, -1, 0, 0, 0),
            new Card("فزت بالبطولة الرمضانية: اقبض ₪100", COLLECT, 100, -1, 0, 0, 0),
            new Card("خذ مشكنتا (قرض عقاري): اقبض ₪150", COLLECT, 150, -1, 0, 0, 0),
            new Card("ادفع ₪50 مخالفة سرعة", PAY, 50, -1, 0, 0, 0)
    );

    public static final List<Card> COMMUNITY_CHEST = List.of(
            new Card("بطاقة شحرور من المسكوبية - احتفظ فيها لحد ما تحتاجها", JAIL_FREE_CARD, 0, -1, 0, 0, 0),
            new Card("ارجع لأبو ديس", MOVE_TO_BACKWARD, 0, 1, 0, 0, 0),
            new Card("ادفع ₪100 تأمين صحي", PAY, 100, -1, 0, 0, 0),
            new Card("بطلعلك ورثة ₪100", COLLECT, 100, -1, 0, 0, 0),
            new Card("لأنك بتلعب مونوبولي بالبلد: خد ₪10", COLLECT, 10, -1, 0, 0, 0),
            new Card("اقبض ₪50 لأنك محترم", COLLECT, 50, -1, 0, 0, 0),
            new Card("استُدعيت للتحقيق بالمسكوبية", GO_TO_JAIL, 0, -1, 0, 0, 0),
            new Card("ادفع ₪50 رخصة سيارة", PAY, 50, -1, 0, 0, 0),
            new Card("ادفع ₪50 دكتور أسنان", PAY, 50, -1, 0, 0, 0),
            new Card("تقدم لخط البداية", MOVE_TO, 0, 0, 0, 0, 0),
            new Card("يوم المهندس العالمي: اقبض ₪200", COLLECT, 200, -1, 0, 0, 0),
            new Card("اقبض ₪50 تعاطف", COLLECT, 50, -1, 0, 0, 0),
            new Card("اقبض ₪50 استرداد ضريبة", COLLECT, 50, -1, 0, 0, 0),
            new Card("عيد ميلادك اليوم: الكل بعايدك ₪10", COLLECT_FROM_ALL, 10, -1, 0, 0, 0),
            new Card("يوم النقاهة: اقبض ₪100", COLLECT, 100, -1, 0, 0, 0),
            new Card("ادفع أرنونا: ₪50 عن كل دار و₪100 عن كل عمارة", PAY_PER_BUILDING, 0, -1, 0, 50, 100)
    );

    public static final List<Card> GATES = List.of(
            new Card("اليوم مش يومك: ادفع ₪50", PAY, 50, -1, 0, 0, 0),
            new Card("الحظ ضارب معك اليوم: اقبض ₪100", COLLECT, 100, -1, 0, 0, 0),
            new Card("اسحب بطاقة فرصة", DRAW_CHANCE, 0, -1, 0, 0, 0),
            new Card("اسحب بطاقة صندوق الجماعة", DRAW_CHEST, 0, -1, 0, 0, 0),
            new Card("ارجع خطوة للخلف", MOVE_RELATIVE, 0, -1, -1, 0, 0),
            new Card("تقدم خطوتين", MOVE_RELATIVE, 0, -1, 2, 0, 0),
            new Card("البوابة مغلقة: تخطى دورك الجاي", GATE_CLOSED, 0, -1, 0, 0, 0)
    );
}