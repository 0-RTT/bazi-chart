"""神煞查法校验。

做法与其它脚本不同：这里**独立实现一遍查法表**（不读 Kotlin），
再与一份实测基准逐柱对撞。

基准盘取自实际排盘工具的结果：

    己卯 甲戌 丙午 壬辰（乾造，年纳音城头土）

    年柱  太极贵人、桃花、空亡
    月柱  太极贵人、华盖
    日柱  天德贵人、月德贵人、德秀贵人、阴差阳错、六秀日、孤鸾煞、勾绞煞、天喜、羊刃
    时柱  太极贵人、童子煞

这盘的价值在于：它同时考到「按年干查」「按日干查」「按月令查」「按年支查」
「按日支查」「按年纳音查」「按日柱干支查」七种基准，且各柱数量悬殊，
顺序错、查法错都会立刻暴露。
"""

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

passed = 0
failed = []


def check(ok, msg):
    global passed
    if ok:
        passed += 1
    else:
        failed.append(msg)


GAN = "甲乙丙丁戊己庚辛壬癸"
ZHI = "子丑寅卯辰巳午未申酉戌亥"

# ------------------------------------------------------------------ 表

TAI_JI = {
    "甲": "子午", "乙": "子午",
    "丙": "卯酉", "丁": "卯酉",
    "戊": "辰戌丑未", "己": "辰戌丑未",
    "庚": "寅亥", "辛": "寅亥",
    "壬": "巳申", "癸": "巳申",
}

TIAN_DE_GAN = {"寅": "丁", "辰": "壬", "巳": "辛", "未": "甲", "申": "癸", "戌": "丙", "亥": "乙", "丑": "庚"}
TIAN_DE_ZHI = {"卯": "申", "午": "亥", "酉": "寅", "子": "巳"}

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

LIU_HE_GROUP = {
    "申": ("酉", "寅", "辰"), "子": ("酉", "寅", "辰"), "辰": ("酉", "寅", "辰"),
    "寅": ("卯", "申", "戌"), "午": ("卯", "申", "戌"), "戌": ("卯", "申", "戌"),
    "巳": ("午", "亥", "丑"), "酉": ("午", "亥", "丑"), "丑": ("午", "亥", "丑"),
    "亥": ("子", "巳", "未"), "卯": ("子", "巳", "未"), "未": ("子", "巳", "未"),
}
# 桃花、驿马、华盖
PEACH = {k: v[0] for k, v in LIU_HE_GROUP.items()}
HORSE = {k: v[1] for k, v in LIU_HE_GROUP.items()}
CANOPY = {k: v[2] for k, v in LIU_HE_GROUP.items()}

TIAN_XI = {
    "子": "酉", "丑": "申", "寅": "未", "卯": "午",
    "辰": "巳", "巳": "辰", "午": "卯", "未": "寅",
    "申": "丑", "酉": "子", "戌": "亥", "亥": "戌",
}

YANG_REN = {
    "甲": "卯", "乙": "寅", "丙": "午", "戊": "午",
    "丁": "巳", "己": "巳", "庚": "酉", "辛": "申",
    "壬": "子", "癸": "亥",
}

YIN_CHA = {"丙子", "丁丑", "戊寅", "辛卯", "壬辰", "癸巳",
           "丙午", "丁未", "戊申", "辛酉", "壬戌", "癸亥"}
LIU_XIU = {"丙午", "丁未", "戊子", "戊午", "己丑", "己未"}
GU_LUAN = {"乙巳", "丁巳", "辛亥", "戊申", "甲寅", "戊午", "壬子", "丙午"}

TONG_ZI = {
    "金": "午卯", "木": "午卯",
    "水": "酉戌", "火": "酉戌",
    "土": "辰巳",
}

# ------------------------------------------------------------------ 查法

# 与 Kotlin 端 rulesOf 的顺序一致：输出顺序即按此排列。
ORDER = [
    "太极贵人", "天乙贵人", "天德贵人", "月德贵人", "德秀贵人",
    "文昌", "禄神", "阴差阳错", "六秀日", "孤鸾煞",
    "勾绞煞", "天喜", "桃花", "驿马", "华盖", "羊刃", "童子煞",
]

TIAN_YI = {
    "甲": "丑未", "戊": "丑未", "庚": "丑未",
    "乙": "子申", "己": "子申",
    "丙": "亥酉", "丁": "亥酉",
    "壬": "卯巳", "癸": "卯巳",
    "辛": "午寅",
}
WEN_CHANG = {
    "甲": "巳", "乙": "午", "丙": "申", "戊": "申", "丁": "酉",
    "己": "酉", "庚": "亥", "辛": "子", "壬": "寅", "癸": "卯",
}
LU_SHEN = {
    "甲": "寅", "乙": "卯", "丙": "巳", "戊": "巳", "丁": "午",
    "己": "午", "庚": "申", "辛": "酉", "壬": "亥", "癸": "子",
}


def shift(zhi, n):
    return ZHI[(ZHI.index(zhi) + n) % 12]


def void_branches(day_gz):
    """日柱旬空两支。"""
    idx = None
    for i in range(60):
        if GAN[i % 10] + ZHI[i % 12] == day_gz:
            idx = i
            break
    assert idx is not None, day_gz
    gan, zhi = idx % 10, idx % 12
    first = ((zhi - gan + 10) % 12 + 12) % 12
    return ZHI[first] + ZHI[(first + 1) % 12]


def analyze(gz, na_yin, male=True):
    year, month, day, hour = gz
    mg, mz = month[0], month[1]
    dg = day[0]
    yz, dz = year[1], day[1]

    out = {i: [] for i in range(4)}
    for i, p in enumerate(gz):
        g, z = p[0], p[1]
        found = []

        if z in TAI_JI[year[0]] or z in TAI_JI[dg]:
            found.append("太极贵人")
        if z in TIAN_YI[dg]:
            found.append("天乙贵人")
        if TIAN_DE_GAN.get(mz) == g or TIAN_DE_ZHI.get(mz) == z:
            found.append("天德贵人")
        if YUE_DE.get(mz) == g:
            found.append("月德贵人")
        if g in DE_XIU.get(mz, ""):
            found.append("德秀贵人")
        if WEN_CHANG[dg] == z:
            found.append("文昌")
        if LU_SHEN[dg] == z:
            found.append("禄神")
        if i == 2 and p in YIN_CHA:
            found.append("阴差阳错")
        if i == 2 and p in LIU_XIU:
            found.append("六秀日")
        if i == 2 and p in GU_LUAN:
            found.append("孤鸾煞")
        if z in (shift(yz, 3), shift(yz, -3)):
            found.append("勾绞煞")
        if TIAN_XI[yz] == z:
            found.append("天喜")
        if z in (PEACH[yz], PEACH[dz]):
            found.append("桃花")
        if z in (HORSE[yz], HORSE[dz]):
            found.append("驿马")
        if z in (CANOPY[yz], CANOPY[dz]):
            found.append("华盖")
        if YANG_REN[dg] == z:
            found.append("羊刃")
        if z in TONG_ZI.get(na_yin, ""):
            found.append("童子煞")

        found.sort(key=lambda n: ORDER.index(n))
        out[i] = found

    void = void_branches(day)
    for i, p in enumerate(gz):
        if p[1] in void:
            out[i].append("空亡")

    return out


# ------------------------------------------------------------------ 断言

BASE = ["己卯", "甲戌", "丙午", "壬辰"]
res = analyze(BASE, "土")

EXPECT = {
    0: ["太极贵人", "桃花", "空亡"],
    1: ["太极贵人", "华盖"],
    2: ["天德贵人", "月德贵人", "德秀贵人", "阴差阳错", "六秀日", "孤鸾煞", "勾绞煞", "天喜", "羊刃"],
    3: ["太极贵人", "童子煞"],
}

NAMES = ["年柱", "月柱", "日柱", "时柱"]
for i in range(4):
    check(res[i] == EXPECT[i], "%s 应为 %s，实得 %s" % (NAMES[i], EXPECT[i], res[i]))

# 逐项复核几处最容易错的查法
check(void_branches("丙午") == "寅卯", "丙午旬空应为寅卯")
check(shift("卯", 3) == "午" and shift("卯", -3) == "子", "勾绞煞前后三辰应为午与子")
check(TIAN_DE_GAN["戌"] == "丙", "戌月天德应为丙")
check("丙" in DE_XIU["戌"], "戌月德秀应含丙")
check(TIAN_XI["卯"] == "午", "卯年天喜应为午")
check(TONG_ZI["土"] == "辰巳", "土命童子煞应为辰巳")

# 「将星」不应出现在任何一柱
for i in range(4):
    check("将星" not in res[i], "%s 不应有将星" % NAMES[i])

# 源码侧：规则必须真的存在，且将星不再生成
src = open(
    os.path.join(ROOT, "core", "analysis", "src", "main", "kotlin", "com", "bazi", "analysis", "ShenSha.kt"),
    encoding="utf-8",
).read()
code = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
code = "\n".join(l.split("//")[0] for l in code.splitlines())

for rule in ["太极贵人", "天德贵人", "月德贵人", "德秀贵人", "阴差阳错",
             "六秀日", "孤鸾煞", "勾绞煞", "天喜", "童子煞", "羊刃", "桃花", "华盖"]:
    check('"%s"' % rule in code, "ShenSha.kt 已无该规则：%s" % rule)

check('"将星"' not in code, "ShenSha.kt 不应再有将星规则")
check("年干${input.yearGan.cn}、日干${dm.cn}并查" in src, "太极贵人应年干日干并查")

print("神煞查法校验")
for i in range(4):
    print("  %s：%s" % (NAMES[i], "、".join(res[i])))
print("共执行 %d 项断言" % passed)
if failed:
    print("失败 %d 项：" % len(failed))
    for f in failed:
        print("  -", f)
    sys.exit(1)
print("全部通过")
