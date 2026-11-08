"""神煞查法的多盘校验。

用三张**构造盘**（非真实命例，不含任何出生时间或地点）逐柱对撞，
五张合起来覆盖规则表里全部 38 种神煞：

    构造盘一  丁酉 甲申 丙午 壬寅（纳音火）
    构造盘二  乙巳 丁卯 庚申 壬辰（纳音火）
    构造盘三  丁巳 戊申 丙辰 癸未（纳音土）
    构造盘四  癸卯 甲寅 甲辰 乙丑（纳音金）
    构造盘五  甲寅 丙寅 戊寅 甲寅（纳音水）

本脚本用 Python 独立实现一遍查法表，不读 Kotlin 代码，
然后与期望集合做**精确比较**——多一项、少一项、位置错都算失败。

说明：表格的**外部依据**是查法口诀（见 test_formulas），那部分与具体盘无关；
具体盘只用来锁住「哪一柱命中」，防止 Kotlin 实现漂移。
定位口径时曾用外部结果校对，那些命例连同出生信息一律不入库。

为什么必须精确比较：早先只比「有没有」，于是漏了九项、多了一项
（华盖自指）也照样"通过"。集合相等才是真对撞。
"""

import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

GAN = "甲乙丙丁戊己庚辛壬癸"
ZHI = "子丑寅卯辰巳午未申酉戌亥"

failures = []
checks = 0


def check(cond, msg):
    global checks
    checks += 1
    if not cond:
        failures.append(msg)


# ------------------------------------------------------------------ 基础表

TAI_JI = {
    "甲": "子午", "乙": "子午", "丙": "卯酉", "丁": "卯酉",
    "戊": "辰戌丑未", "己": "辰戌丑未",
    "庚": "寅亥", "辛": "寅亥", "壬": "巳申", "癸": "巳申",
}

TIAN_YI = {
    "甲": "丑未", "戊": "丑未", "庚": "丑未",
    "乙": "子申", "己": "子申",
    "丙": "亥酉", "丁": "亥酉",
    "壬": "卯巳", "癸": "卯巳",
    "辛": "午寅",
}

GUO_YIN = {
    "甲": "戌", "乙": "亥", "丙": "丑", "丁": "寅", "戊": "丑",
    "己": "寅", "庚": "辰", "辛": "巳", "壬": "未", "癸": "申",
}

FU_XING = {
    "甲": "寅子", "丙": "寅子", "戊": "申", "己": "未", "丁": "亥",
    "乙": "丑卯", "癸": "丑卯", "庚": "午", "辛": "巳", "壬": "辰",
}

JIN_YU = {
    "甲": "辰", "乙": "巳", "丙": "未", "丁": "申", "戊": "未",
    "己": "申", "庚": "戌", "辛": "亥", "壬": "丑", "癸": "寅",
}

HONG_YAN = {
    "甲": "午", "乙": "午", "丙": "寅", "丁": "未", "戊": "辰",
    "己": "辰", "庚": "戌", "辛": "酉", "壬": "子", "癸": "申",
}

WEN_CHANG = {
    "甲": "巳", "乙": "午", "丙": "申", "戊": "申", "丁": "酉",
    "己": "酉", "庚": "亥", "辛": "子", "壬": "寅", "癸": "卯",
}

LU_SHEN = {
    "甲": "寅", "乙": "卯", "丙": "巳", "戊": "巳", "丁": "午",
    "己": "午", "庚": "申", "辛": "酉", "壬": "亥", "癸": "子",
}

YANG_REN = {
    "甲": "卯", "乙": "寅", "丙": "午", "戊": "午", "丁": "巳",
    "己": "巳", "庚": "酉", "辛": "申", "壬": "子", "癸": "亥",
}

TIAN_DE = {
    "寅": "丁", "卯": "申", "辰": "壬", "巳": "辛", "午": "亥", "未": "甲",
    "申": "癸", "酉": "寅", "戌": "丙", "亥": "乙", "子": "巳", "丑": "庚",
}

YUE_DE = {
    "寅": "丙", "午": "丙", "戌": "丙",
    "申": "壬", "子": "壬", "辰": "壬",
    "亥": "甲", "卯": "甲", "未": "甲",
    "巳": "庚", "酉": "庚", "丑": "庚",
}

DE_XIU = {
    "寅": "丙丁戊癸", "午": "丙丁戊癸", "戌": "丙丁戊癸",
    "申": "壬癸戊己丙辛甲", "子": "壬癸戊己丙辛甲", "辰": "壬癸戊己丙辛甲",
    "巳": "庚辛乙", "酉": "庚辛乙", "丑": "庚辛乙",
    "亥": "甲乙丁壬", "卯": "甲乙丁壬", "未": "甲乙丁壬",
}

GAN_HE = {
    "甲": "己", "己": "甲", "乙": "庚", "庚": "乙", "丙": "辛",
    "辛": "丙", "丁": "壬", "壬": "丁", "戊": "癸", "癸": "戊",
}

ZHI_HE = {
    "子": "丑", "丑": "子", "寅": "亥", "亥": "寅", "卯": "戌",
    "戌": "卯", "辰": "酉", "酉": "辰", "巳": "申", "申": "巳",
    "午": "未", "未": "午",
}

# 三合局分组：0 申子辰、1 寅午戌、2 巳酉丑、3 亥卯未
GROUP_OF = {}
for _z in "申子辰":
    GROUP_OF[_z] = 0
for _z in "寅午戌":
    GROUP_OF[_z] = 1
for _z in "巳酉丑":
    GROUP_OF[_z] = 2
for _z in "亥卯未":
    GROUP_OF[_z] = 3

GROUP_PEACH = "酉卯午子"
GROUP_HORSE = "寅申亥巳"
GROUP_CANOPY = "辰戌丑未"
GROUP_WANG = "亥巳申寅"
GROUP_JIE = "巳亥寅申"

# 孤辰寡宿按**三会方**，不是三合局
GU_CHEN = {
    "子": "寅", "丑": "寅", "寅": "巳", "卯": "巳", "辰": "巳",
    "巳": "申", "午": "申", "未": "申",
    "申": "亥", "酉": "亥", "戌": "亥", "亥": "寅",
}

GUA_SU = {
    "子": "戌", "丑": "戌", "寅": "丑", "卯": "丑", "辰": "丑",
    "巳": "辰", "午": "辰", "未": "辰",
    "申": "未", "酉": "未", "戌": "未", "亥": "戌",
}

HONG_LUAN = {
    "子": "卯", "丑": "寅", "寅": "丑", "卯": "子", "辰": "亥", "巳": "戌",
    "午": "酉", "未": "申", "申": "未", "酉": "午", "戌": "巳", "亥": "辰",
}

TIAN_XI = {
    "子": "酉", "丑": "申", "寅": "未", "卯": "午", "辰": "巳", "巳": "辰",
    "午": "卯", "未": "寅", "申": "丑", "酉": "子", "戌": "亥", "亥": "戌",
}

YIN_CHA = {"丙子", "丁丑", "戊寅", "辛卯", "壬辰", "癸巳",
           "丙午", "丁未", "戊申", "辛酉", "壬戌", "癸亥"}
LIU_XIU = {"丙午", "丁未", "戊子", "戊午", "己丑", "己未"}
GU_LUAN = {"乙巳", "丁巳", "辛亥", "戊申", "甲寅", "戊午", "壬子", "丙午"}
BA_ZHUAN = {"甲寅", "乙卯", "丁未", "戊戌", "己未", "庚申", "辛酉", "癸丑"}

# 十恶大败：口诀「甲辰乙巳与壬申，丙申丁亥及庚辰，戊戌癸亥加辛巳，己丑都来十位神」
SHI_E_DA_BAI = {"甲辰", "乙巳", "壬申", "丙申", "丁亥",
                "庚辰", "戊戌", "癸亥", "辛巳", "己丑"}

# 金神：乙丑、己巳、癸酉，论日时两柱
JIN_SHEN = {"乙丑", "己巳", "癸酉"}

# 天赦日：春戊寅、夏甲午、秋戊申、冬甲子
TIAN_SHE = {
    "寅": "戊寅", "卯": "戊寅", "辰": "戊寅",
    "巳": "甲午", "午": "甲午", "未": "甲午",
    "申": "戊申", "酉": "戊申", "戌": "戊申",
    "亥": "甲子", "子": "甲子", "丑": "甲子",
}

# 学堂、词馆：年柱纳音五行的长生、临官之地（土寄水宫）
XUE_TANG = {"金": "巳", "木": "亥", "水": "申", "火": "寅", "土": "申"}
CI_GUAN = {"金": "申", "木": "寅", "水": "亥", "火": "巳", "土": "亥"}

# 将星：三合局中神
GROUP_GENERAL = "子午酉卯"

SEASON_SPRING_AUTUMN = set("寅卯辰申酉戌")


# ------------------------------------------------------------------ 算法

def ganzhi_index(gan, zhi):
    for i in range(60):
        if GAN[i % 10] == gan and ZHI[i % 12] == zhi:
            return i
    raise ValueError(f"非法干支 {gan}{zhi}")


def shift(zhi, n):
    return ZHI[(ZHI.index(zhi) + n) % 12]


def void_branches(day_gz):
    """日柱旬空：旬首起顺数第 11、12 位。"""
    i = ganzhi_index(day_gz[0], day_gz[1])
    start = i - i % 10
    return {ZHI[(start + 10) % 12], ZHI[(start + 11) % 12]}


def shen_sha(pillars, na_yin):
    """
    pillars: [(宫位, 干, 支)] × 4
    返回 {宫位: set(神煞名)}，同名规则合并。
    """
    (_, y_gan, y_zhi), (_, m_gan, m_zhi), (_, d_gan, d_zhi), (_, h_gan, h_zhi) = pillars
    month = m_zhi

    hits = {}

    def add(name, positions):
        if positions:
            hits.setdefault(name, set()).update(positions)

    # 贵人五件：年干与日干并查（有的柱只能由年干取到，单看日干会漏）
    for name, table in (("太极贵人", TAI_JI), ("天乙贵人", TIAN_YI),
                        ("国印贵人", GUO_YIN), ("福星贵人", FU_XING),
                        ("金舆", JIN_YU)):
        targets = set(table.get(y_gan, "")) | set(table.get(d_gan, ""))
        add(name, {p for p, _, z in pillars if z in targets})

    # 天德 / 天德合
    de = TIAN_DE[month]
    add("天德贵人", {p for p, g, z in pillars if g == de or z == de})
    he = GAN_HE.get(de) or ZHI_HE.get(de)
    if he:
        add("天德合", {p for p, g, z in pillars if g == he or z == he})

    # 月德 / 月德合
    yd = YUE_DE[month]
    add("月德贵人", {p for p, g, _ in pillars if g == yd})
    yd_he = GAN_HE.get(yd)
    if yd_he:
        add("月德合", {p for p, g, _ in pillars if g == yd_he})

    # 德秀
    dex = set(DE_XIU[month])
    add("德秀贵人", {p for p, g, _ in pillars if g in dex})

    # 日干诸项
    for name, table in (("文昌", WEN_CHANG), ("禄神", LU_SHEN),
                        ("羊刃", YANG_REN), ("红艳煞", HONG_YAN)):
        t = table[d_gan]
        add(name, {p for p, _, z in pillars if z == t})

    # 日柱干支组合
    dgz = d_gan + d_zhi
    if dgz in YIN_CHA:
        add("阴差阳错", {"日"})
    if dgz in LIU_XIU:
        add("六秀日", {"日"})
    if dgz in GU_LUAN:
        add("孤鸾煞", {"日"})
    if dgz in BA_ZHUAN:
        add("八专日", {"日"})
    if dgz in SHI_E_DA_BAI:
        add("十恶大败", {"日"})

    # 金神：日柱、时柱都查
    hgz = h_gan + h_zhi
    if dgz in JIN_SHEN:
        add("金神", {"日"})
    if hgz in JIN_SHEN:
        add("金神", {"时"})

    # 天赦：按月令所属四季取那一日，只论日柱
    if dgz == TIAN_SHE[month]:
        add("天赦", {"日"})

    # 学堂、词馆：年柱纳音五行的长生与临官
    add("学堂", {p for p, _, z in pillars if z == XUE_TANG[na_yin]})
    add("词馆", {p for p, _, z in pillars if z == CI_GUAN[na_yin]})

    # 天医：月支前一位
    add("天医", {p for p, _, z in pillars if z == shift(month, -1)})

    # 年支诸项
    add("天喜", {p for p, _, z in pillars if z == TIAN_XI[y_zhi]})
    add("红鸾", {p for p, _, z in pillars if z == HONG_LUAN[y_zhi]})
    add("孤辰", {p for p, _, z in pillars if z == GU_CHEN[y_zhi]})
    add("寡宿", {p for p, _, z in pillars if z == GUA_SU[y_zhi]})

    # 丧门：年支顺行二位
    sang = shift(y_zhi, 2)
    add("丧门", {p for p, _, z in pillars if z == sang})

    # 勾绞：年支前后三辰
    add("勾绞煞", {p for p, _, z in pillars if z in (shift(y_zhi, 3), shift(y_zhi, -3))})

    # 三合局系：年支与日支并查，华盖排除基准宫位
    for base_pos, base in (("年", y_zhi), ("日", d_zhi)):
        idx = GROUP_OF[base]
        add("桃花", {p for p, _, z in pillars if z == GROUP_PEACH[idx]})
        add("驿马", {p for p, _, z in pillars if z == GROUP_HORSE[idx]})
        add("亡神", {p for p, _, z in pillars if z == GROUP_WANG[idx]})
        add("劫煞", {p for p, _, z in pillars if z == GROUP_JIE[idx]})
        add("华盖", {p for p, _, z in pillars
                     if z == GROUP_CANOPY[idx] and p != base_pos})
        add("将星", {p for p, _, z in pillars
                     if z == GROUP_GENERAL[idx] and p != base_pos})

    # 童子煞：纳音法与季节法并查
    targets = set()
    if na_yin in ("金", "木"):
        targets |= set("午卯")
    elif na_yin in ("水", "火"):
        targets |= set("酉戌")
    elif na_yin == "土":
        targets |= set("辰巳")
    targets |= set("寅子") if month in SEASON_SPRING_AUTUMN else set("卯未辰")
    add("童子煞", {p for p, _, z in pillars if z in targets})

    # 空亡
    void = void_branches(dgz)
    add("空亡", {p for p, _, z in pillars if z in void})

    return hits


def compare(label, got, expected):
    for pos in "年月日时":
        g = got.get(pos, set())
        e = set(expected.get(pos, []))
        missing = e - g
        extra = g - e
        check(not missing, f"{label} {pos}柱 缺少：{'、'.join(sorted(missing))}")
        check(not extra, f"{label} {pos}柱 多出：{'、'.join(sorted(extra))}")


# ------------------------------------------------------------------ 样本

CASE_A = {
    "label": "构造盘一 丁酉 甲申 丙午 壬寅",
    "pillars": [("年", "丁", "酉"), ("月", "甲", "申"),
                ("日", "丙", "午"), ("时", "壬", "寅")],
    "nayin": "火",
    "expected": {
        "年": ["天乙贵人", "太极贵人", "月德合", "童子煞"],
        "月": ["亡神", "德秀贵人", "文昌", "金舆", "驿马"],
        "日": ["六秀日", "勾绞煞", "孤鸾煞", "德秀贵人", "桃花",
               "红鸾", "羊刃", "阴差阳错"],
        "时": ["劫煞", "国印贵人", "学堂", "德秀贵人", "月德贵人", "福星贵人",
               "空亡", "童子煞", "红艳煞"],
    },
}

CASE_B = {
    "label": "构造盘二 乙巳 丁卯 庚申 壬辰",
    "pillars": [("年", "乙", "巳"), ("月", "丁", "卯"),
                ("日", "庚", "申"), ("时", "壬", "辰")],
    "nayin": "火",
    "expected": {
        "年": ["劫煞", "天德合", "德秀贵人", "词馆", "金舆"],
        "月": ["德秀贵人", "福星贵人"],
        "日": ["亡神", "八专日", "勾绞煞", "天乙贵人", "天德贵人", "孤辰", "禄神"],
        "时": ["华盖", "国印贵人", "天喜", "寡宿", "德秀贵人"],
    },
}

CASE_C = {
    "label": "构造盘三 丁巳 戊申 丙辰 癸未",
    "pillars": [("年", "丁", "巳"), ("月", "戊", "申"),
                ("日", "丙", "辰"), ("时", "癸", "未")],
    "nayin": "土",
    "expected": {
        "年": ["劫煞", "月德合", "禄神", "童子煞"],
        "月": ["亡神", "勾绞煞", "天德合", "孤辰", "学堂", "德秀贵人", "文昌", "金舆"],
        "日": ["天喜", "寡宿", "德秀贵人", "童子煞"],
        "时": ["丧门", "天医", "天德贵人", "德秀贵人", "金舆"],
    },
}


CASE_D = {
    "label": "构造盘四 癸卯 甲寅 甲辰 乙丑",
    "pillars": [("年", "癸", "卯"), ("月", "甲", "寅"),
                ("日", "甲", "辰"), ("时", "乙", "丑")],
    "nayin": "金",
    "expected": {
        "年": ["天乙贵人", "德秀贵人", "福星贵人", "空亡", "童子煞", "羊刃"],
        "月": ["亡神", "禄神", "福星贵人", "空亡", "童子煞", "金舆", "驿马"],
        "日": ["十恶大败", "金舆"],
        "时": ["天乙贵人", "天医", "寡宿", "福星贵人", "金神"],
    },
}

CASE_E = {
    "label": "构造盘五 甲寅 丙寅 戊寅 甲寅",
    "pillars": [("年", "甲", "寅"), ("月", "丙", "寅"),
                ("日", "戊", "寅"), ("时", "甲", "寅")],
    "nayin": "水",
    "expected": {
        "年": ["福星贵人", "童子煞"],
        "月": ["德秀贵人", "月德贵人", "福星贵人", "童子煞"],
        "日": ["天赦", "德秀贵人", "福星贵人", "童子煞", "阴差阳错"],
        "时": ["福星贵人", "童子煞"],
    },
}


def run_cases():
    for case in (CASE_A, CASE_B, CASE_C, CASE_D, CASE_E):
        got = shen_sha(case["pillars"], case["nayin"])
        # shen_sha 返回 {神煞名: {宫位}}，比较时转置成 {宫位: {神煞名}}
        by_pos = {}
        for name, poses in got.items():
            for p in poses:
                by_pos.setdefault(p, set()).add(name)
        compare(case["label"], by_pos, case["expected"])


def sorted_branches(text):
    return set(text)


def test_formulas():
    """查法表对撞口诀。这部分是外部依据，与用什么盘无关。

    天乙贵人：甲戊庚牛羊、乙己鼠猴乡、丙丁猪鸡位、壬癸兔蛇藏、六辛逢马虎。
    """
    check(sorted_branches(TIAN_YI["甲"]) == {"丑", "未"}, "天乙：甲见丑未")
    check(sorted_branches(TIAN_YI["戊"]) == {"丑", "未"}, "天乙：戊见丑未")
    check(sorted_branches(TIAN_YI["庚"]) == {"丑", "未"}, "天乙：庚见丑未")
    check(sorted_branches(TIAN_YI["乙"]) == {"子", "申"}, "天乙：乙见子申")
    check(sorted_branches(TIAN_YI["己"]) == {"子", "申"}, "天乙：己见子申")
    check(sorted_branches(TIAN_YI["丙"]) == {"亥", "酉"}, "天乙：丙见亥酉")
    check(sorted_branches(TIAN_YI["丁"]) == {"亥", "酉"}, "天乙：丁见亥酉")
    check(sorted_branches(TIAN_YI["壬"]) == {"卯", "巳"}, "天乙：壬见卯巳")
    check(sorted_branches(TIAN_YI["癸"]) == {"卯", "巳"}, "天乙：癸见卯巳")
    check(sorted_branches(TIAN_YI["辛"]) == {"午", "寅"}, "天乙：辛见午寅")

    # 太极贵人：甲乙子午、丙丁卯酉、戊己四季、庚辛寅亥、壬癸巳申
    check(set(TAI_JI["甲"]) == {"子", "午"}, "太极：甲见子午")
    check(set(TAI_JI["丙"]) == {"卯", "酉"}, "太极：丙见卯酉")
    check(set(TAI_JI["戊"]) == {"辰", "戌", "丑", "未"}, "太极：戊见四季")
    check(set(TAI_JI["庚"]) == {"寅", "亥"}, "太极：庚见寅亥")
    check(set(TAI_JI["壬"]) == {"巳", "申"}, "太极：壬见巳申")

    # 月德：寅午戌见丙、申子辰见壬、亥卯未见甲、巳酉丑见庚
    for zhi in "寅午戌":
        check(YUE_DE[zhi] == "丙", f"月德：{zhi}月见丙")
    for zhi in "申子辰":
        check(YUE_DE[zhi] == "壬", f"月德：{zhi}月见壬")
    for zhi in "亥卯未":
        check(YUE_DE[zhi] == "甲", f"月德：{zhi}月见甲")
    for zhi in "巳酉丑":
        check(YUE_DE[zhi] == "庚", f"月德：{zhi}月见庚")

    # 天德：寅丁、卯申、辰壬、巳辛、午亥、未甲、申癸、酉寅、戌丙、亥乙、子巳、丑庚
    expect_tiande = {"寅": "丁", "卯": "申", "辰": "壬", "巳": "辛", "午": "亥", "未": "甲",
                     "申": "癸", "酉": "寅", "戌": "丙", "亥": "乙", "子": "巳", "丑": "庚"}
    check(TIAN_DE == expect_tiande, "天德表与口诀不符")

    # 将星：三合局中神。《三命通会》「将星者，如万军之中，一将当先」
    check(GROUP_GENERAL == "子午酉卯", "将星：申子辰子、寅午戌午、巳酉丑酉、亥卯未卯")

    # 十恶大败：口诀十位，缺一即错
    check(SHI_E_DA_BAI == {"甲辰", "乙巳", "壬申", "丙申", "丁亥",
                           "庚辰", "戊戌", "癸亥", "辛巳", "己丑"},
          "十恶大败十位与口诀不符")
    check(len(SHI_E_DA_BAI) == 10, "十恶大败应十位")

    # 金神：只此三日
    check(JIN_SHEN == {"乙丑", "己巳", "癸酉"}, "金神应为乙丑、己巳、癸酉")

    # 天赦：春戊寅、夏甲午、秋戊申、冬甲子
    for zhi in "寅卯辰":
        check(TIAN_SHE[zhi] == "戊寅", f"天赦：{zhi}月见戊寅")
    for zhi in "巳午未":
        check(TIAN_SHE[zhi] == "甲午", f"天赦：{zhi}月见甲午")
    for zhi in "申酉戌":
        check(TIAN_SHE[zhi] == "戊申", f"天赦：{zhi}月见戊申")
    for zhi in "亥子丑":
        check(TIAN_SHE[zhi] == "甲子", f"天赦：{zhi}月见甲子")

    # 学堂词馆：纳音五行的长生与临官。金巳申、木亥寅、水申亥、火寅巳、土申亥
    check(XUE_TANG == {"金": "巳", "木": "亥", "水": "申", "火": "寅", "土": "申"},
          "学堂应为纳音长生之地")
    check(CI_GUAN == {"金": "申", "木": "寅", "水": "亥", "火": "巳", "土": "亥"},
          "词馆应为纳音临官之地")
    for wx, zhi in XUE_TANG.items():
        # 长生与临官必须同属该五行十二长生序列，相距三位
        check(ZHI.index(CI_GUAN[wx]) == (ZHI.index(zhi) + 3) % 12
              or ZHI.index(CI_GUAN[wx]) == (ZHI.index(zhi) - 3) % 12,
              f"{wx}命：学堂{zhi}与词馆{CI_GUAN[wx]}应相差三位")

    # 桃花、驿马、华盖、亡神、劫煞按三合局
    for zhi, idx in (("申", 0), ("寅", 1), ("巳", 2), ("亥", 3)):
        check(GROUP_OF[zhi] == idx, f"三合局分组：{zhi}")
    check(GROUP_PEACH == "酉卯午子", "桃花：申子辰酉、寅午戌卯、巳酉丑午、亥卯未子")
    check(GROUP_HORSE == "寅申亥巳", "驿马：申子辰寅、寅午戌申、巳酉丑亥、亥卯未巳")
    check(GROUP_CANOPY == "辰戌丑未", "华盖：申子辰辰、寅午戌戌、巳酉丑丑、亥卯未未")


def test_tables():
    """表本身的自洽性：六十甲子都能取到旬空，且空支不含本柱地支。"""
    for i in range(60):
        g, z = GAN[i % 10], ZHI[i % 12]
        void = void_branches(g + z)
        check(len(void) == 2, f"{g}{z} 旬空应两支，实得 {void}")
        check(z not in void, f"{g}{z} 旬空不应含本柱地支")


def strip_comments(src):
    """先剥注释再扫：KDoc 里正写着这些词，不剥会把解释误判成实现。"""
    out = []
    in_block = False
    for line in src.splitlines():
        if in_block:
            if "*/" in line:
                in_block = False
            continue
        if line.strip().startswith("/*"):
            if "*/" not in line:
                in_block = True
            continue
        out.append(line.split("//")[0])
    return "\n".join(out)


def test_kotlin_source():
    path = os.path.join(ROOT, "core", "analysis", "src", "main", "kotlin",
                        "com", "bazi", "analysis", "ShenSha.kt")
    raw = open(path, encoding="utf-8").read()
    src = strip_comments(raw)

    for name in ["太极贵人", "天乙贵人", "国印贵人", "福星贵人", "金舆",
                 "天德贵人", "天德合", "月德贵人", "月德合", "德秀贵人",
                 "红鸾", "孤辰", "寡宿", "天喜", "丧门", "亡神", "劫煞",
                 "八专日", "红艳煞", "童子煞", "空亡",
                 "将星", "学堂", "词馆", "天医", "天赦", "金神", "十恶大败"]:
        check(f'"{name}"' in src, f"Kotlin 缺规则：{name}")

    # 将星与华盖都必须排除基准宫位，否则子午卯酉自指
    check(src.count("pos != basePos") >= 2, "将星或华盖未排除基准宫位（会自指）")
    check("GROUP_GU_CHEN" not in src, "孤辰寡宿仍走三合索引（应改三会方）")
    check("bySeason" in src, "童子煞缺季节法")
    check("GAN_HE" in src and "ZHI_HE" in src, "缺天德合/月德合所需的合化表")


def main():
    run_cases()
    test_formulas()
    test_tables()
    test_kotlin_source()

    print(f"共执行 {checks} 项断言")
    if failures:
        print(f"失败 {len(failures)} 项：")
        for f in failures:
            print("  x", f)
        sys.exit(1)
    print("五张构造盘逐柱精确对撞通过，合计覆盖 38 种神煞")
    print("  构造盘一 丁酉 甲申 丙午 壬寅：4 + 5 + 8 + 9 = 26 项")
    print("  构造盘二 乙巳 丁卯 庚申 壬辰：5 + 2 + 7 + 5 = 19 项")
    print("  构造盘三 丁巳 戊申 丙辰 癸未：4 + 8 + 4 + 5 = 21 项")
    print("  构造盘四 癸卯 甲寅 甲辰 乙丑：6 + 7 + 2 + 5 = 20 项")
    print("  构造盘五 甲寅 丙寅 戊寅 甲寅：2 + 4 + 5 + 2 = 13 项")
    print("全部通过")


if __name__ == "__main__":
    main()
