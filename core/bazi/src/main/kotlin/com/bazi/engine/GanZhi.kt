package com.bazi.engine

/** 五行。 */
enum class WuXing(val cn: String) {
    MU("木"), HUO("火"), TU("土"), JIN("金"), SHUI("水");

    /** 我所生者（木生火）。 */
    fun generates(): WuXing = when (this) {
        MU -> HUO; HUO -> TU; TU -> JIN; JIN -> SHUI; SHUI -> MU
    }

    /** 我所克者（木克土）。 */
    fun overcomes(): WuXing = when (this) {
        MU -> TU; TU -> SHUI; SHUI -> HUO; HUO -> JIN; JIN -> MU
    }

    /** 生我者。 */
    fun generatedBy(): WuXing = entries.first { it.generates() == this }

    /** 克我者。 */
    fun overcomeBy(): WuXing = entries.first { it.overcomes() == this }
}

/** 天干。序号即甲=0 … 癸=9。 */
enum class TianGan(val cn: String, val wuXing: WuXing, val yang: Boolean) {
    JIA("甲", WuXing.MU, true),
    YI("乙", WuXing.MU, false),
    BING("丙", WuXing.HUO, true),
    DING("丁", WuXing.HUO, false),
    WU("戊", WuXing.TU, true),
    JI("己", WuXing.TU, false),
    GENG("庚", WuXing.JIN, true),
    XIN("辛", WuXing.JIN, false),
    REN("壬", WuXing.SHUI, true),
    GUI("癸", WuXing.SHUI, false);

    val index: Int get() = ordinal

    /** 阴阳标记文本。 */
    val polarity: String get() = if (yang) "阳" else "阴"

    companion object {
        fun of(name: String): TianGan =
            entries.firstOrNull { it.cn == name }
                ?: throw IllegalArgumentException("未知天干: $name")

        fun ofOrNull(name: String): TianGan? = entries.firstOrNull { it.cn == name }
    }
}

/** 地支。序号即子=0 … 亥=11。 */
enum class DiZhi(val cn: String, val wuXing: WuXing, val yang: Boolean) {
    ZI("子", WuXing.SHUI, true),
    CHOU("丑", WuXing.TU, false),
    YIN("寅", WuXing.MU, true),
    MAO("卯", WuXing.MU, false),
    CHEN("辰", WuXing.TU, true),
    SI("巳", WuXing.HUO, false),
    WU("午", WuXing.HUO, true),
    WEI("未", WuXing.TU, false),
    SHEN("申", WuXing.JIN, true),
    YOU("酉", WuXing.JIN, false),
    XU("戌", WuXing.TU, true),
    HAI("亥", WuXing.SHUI, false);

    val index: Int get() = ordinal

    /** 对应时辰区间，如 子 → 23:00–00:59。 */
    val hourRange: String
        get() {
            val start = (ordinal * 2 + 23) % 24
            val end = (start + 2) % 24
            return "%02d:00–%02d:59".format(start, (end + 23) % 24)
        }

    companion object {
        fun of(name: String): DiZhi =
            entries.firstOrNull { it.cn == name }
                ?: throw IllegalArgumentException("未知地支: $name")

        fun ofOrNull(name: String): DiZhi? = entries.firstOrNull { it.cn == name }

        /** 由时辰序号取地支（0=子时）。 */
        fun fromHourIndex(indexInDay: Int): DiZhi = entries[((indexInDay % 12) + 12) % 12]
    }
}

/** 十神五类。用于统计与格局判定，避免在业务里写 10 个分支。 */
enum class TenGodCategory(val cn: String) {
    SELF("比劫"), OUTPUT("食伤"), WEALTH("财"), OFFICER("官杀"), RESOURCE("印")
}

/** 十神。 */
enum class TenGod(val cn: String, val short: String, val category: TenGodCategory) {
    BI_JIAN("比肩", "比", TenGodCategory.SELF),
    JIE_CAI("劫财", "劫", TenGodCategory.SELF),
    SHI_SHEN("食神", "食", TenGodCategory.OUTPUT),
    SHANG_GUAN("伤官", "伤", TenGodCategory.OUTPUT),
    PIAN_CAI("偏财", "才", TenGodCategory.WEALTH),
    ZHENG_CAI("正财", "财", TenGodCategory.WEALTH),
    QI_SHA("七杀", "杀", TenGodCategory.OFFICER),
    ZHENG_GUAN("正官", "官", TenGodCategory.OFFICER),
    PIAN_YIN("偏印", "枭", TenGodCategory.RESOURCE),
    ZHENG_YIN("正印", "印", TenGodCategory.RESOURCE);

    companion object {
        /**
         * 以日干为「我」，求目标天干的十神。
         *
         * 判据是二维的：五行生克关系 × 阴阳同异。同性别为偏，异性为正。
         */
        fun between(dayMaster: TianGan, target: TianGan): TenGod {
            val samePolarity = dayMaster.yang == target.yang
            val me = dayMaster.wuXing
            val other = target.wuXing
            return when {
                other == me -> if (samePolarity) BI_JIAN else JIE_CAI
                me.generates() == other -> if (samePolarity) SHI_SHEN else SHANG_GUAN
                me.overcomes() == other -> if (samePolarity) PIAN_CAI else ZHENG_CAI
                other.overcomes() == me -> if (samePolarity) QI_SHA else ZHENG_GUAN
                else -> if (samePolarity) PIAN_YIN else ZHENG_YIN
            }
        }
    }
}

/** 四柱位置。 */
enum class PillarPosition(val cn: String) {
    YEAR("年柱"), MONTH("月柱"), DAY("日柱"), HOUR("时柱")
}
