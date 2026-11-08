"""
排盘基础表校验。

目的：在没有 JDK/Android SDK 的环境下，用与 Kotlin 实现完全相同的表结构和算法，
对撞权威输出，确认 十神 / 藏干 / 纳音 / 十二长生 / 空亡 / 六十甲子 的正确性。

基准来源：
- 纳音与四柱：6tail/lunar 官方文档示例输出
  「一九八六年四月廿一 丙寅年 癸巳月 癸酉日 子时 纳音[炉中火 长流水 剑锋金 桑柘木]」
- 十神、长生、空亡：子平法通行表

运行：python tools/verify/verify_tables.py
"""

from __future__ import annotations

import sys

# ---------------------------------------------------------------- 干支

TIAN_GAN = ["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"]
DI_ZHI = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]

GAN_YANG = [i % 2 == 0 for i in range(10)]
ZHI_YANG = [i % 2 == 0 for i in range(12)]

WU_XING_GAN = ["木", "木", "火", "火", "土", "土", "金", "金", "水", "水"]
WU_XING_ZHI = ["水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"]

GENERATES = {"木": "火", "火": "土", "土": "金", "金": "水", "水": "木"}
OVERCOMES = {"木": "土", "土": "水", "水": "火", "火": "金", "金": "木"}

# 六十甲子
JIA_ZI = [TIAN_GAN[i % 10] + DI_ZHI[i % 12] for i in range(60)]


def ganzhi_index(gz: str) -> int:
    return JIA_ZI.index(gz)


# ---------------------------------------------------------------- 十神

def ten_god(day_gan: str, target_gan: str) -> str:
    di = TIAN_GAN.index(day_gan)
    ti = TIAN_GAN.index(target_gan)
    same = GAN_YANG[di] == GAN_YANG[ti]
    me, other = WU_XING_GAN[di], WU_XING_GAN[ti]
    if other == me:
        return "比肩" if same else "劫财"
    if GENERATES[me] == other:
        return "食神" if same else "伤官"
    if OVERCOMES[me] == other:
        return "偏财" if same else "正财"
    if OVERCOMES[other] == me:
        return "七杀" if same else "正官"
    return "偏印" if same else "正印"


# ---------------------------------------------------------------- 藏干

HIDDEN = {
    "子": [("癸", "本气", 1.0)],
    "丑": [("己", "本气", 1.0), ("癸", "中气", 0.5), ("辛", "余气", 0.2)],
    "寅": [("甲", "本气", 1.0), ("丙", "中气", 0.5), ("戊", "余气", 0.2)],
    "卯": [("乙", "本气", 1.0)],
    "辰": [("戊", "本气", 1.0), ("乙", "中气", 0.5), ("癸", "余气", 0.2)],
    "巳": [("丙", "本气", 1.0), ("庚", "中气", 0.5), ("戊", "余气", 0.2)],
    "午": [("丁", "本气", 1.0), ("己", "中气", 0.5)],
    "未": [("己", "本气", 1.0), ("丁", "中气", 0.5), ("乙", "余气", 0.2)],
    "申": [("庚", "本气", 1.0), ("壬", "中气", 0.5), ("戊", "余气", 0.2)],
    "酉": [("辛", "本气", 1.0)],
    "戌": [("戊", "本气", 1.0), ("辛", "中气", 0.5), ("丁", "余气", 0.2)],
    "亥": [("壬", "本气", 1.0), ("甲", "中气", 0.5)],
}

# ---------------------------------------------------------------- 纳音

NA_YIN = [
    "海中金", "炉中火", "大林木", "路旁土", "剑锋金",
    "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
    "泉中水", "屋上土", "霹雳火", "松柏木", "长流水",
    "沙中金", "山下火", "平地木", "壁上土", "金箔金",
    "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木",
    "大溪水", "沙中土", "天上火", "石榴木", "大海水",
]

# ---------------------------------------------------------------- 十二长生

PHASES = ["长生", "沐浴", "冠带", "临官", "帝旺", "衰", "病", "死", "墓", "绝", "胎", "养"]

CHANG_SHENG_START = {
    "甲": "亥", "乙": "午", "丙": "寅", "丁": "酉", "戊": "寅",
    "己": "酉", "庚": "巳", "辛": "子", "壬": "申", "癸": "卯",
}


def growth_phase(gan: str, zhi: str) -> str:
    start = DI_ZHI.index(CHANG_SHENG_START[gan])
    if GAN_YANG[TIAN_GAN.index(gan)]:
        offset = (DI_ZHI.index(zhi) - start + 12) % 12
    else:
        offset = (start - DI_ZHI.index(zhi) + 12) % 12
    return PHASES[offset]


# ---------------------------------------------------------------- 空亡

def void_branches(gz: str) -> list[str]:
    idx = ganzhi_index(gz)
    gan, zhi = idx % 10, idx % 12
    first = ((zhi - gan + 10) % 12 + 12) % 12
    return [DI_ZHI[first], DI_ZHI[(first + 1) % 12]]


# ---------------------------------------------------------------- 校验

FAILS: list[str] = []
CHECKS = 0


def check(label: str, actual, expected) -> None:
    global CHECKS
    CHECKS += 1
    if actual != expected:
        FAILS.append(f"{label}: 期望 {expected}，实际 {actual}")


def main() -> int:
    # 1. 六十甲子结构与序号
    check("甲子序号", ganzhi_index("甲子"), 0)
    check("癸亥序号", ganzhi_index("癸亥"), 59)
    check("丙寅序号", ganzhi_index("丙寅"), 2)
    check("癸酉序号", ganzhi_index("癸酉"), 9)
    check("壬子序号", ganzhi_index("壬子"), 48)

    # 2. 纳音 —— 对撞 6tail 官方示例：丙寅 癸巳 癸酉 壬子
    check("纳音 丙寅", NA_YIN[ganzhi_index("丙寅") // 2], "炉中火")
    check("纳音 癸巳", NA_YIN[ganzhi_index("癸巳") // 2], "长流水")
    check("纳音 癸酉", NA_YIN[ganzhi_index("癸酉") // 2], "剑锋金")
    check("纳音 壬子", NA_YIN[ganzhi_index("壬子") // 2], "桑柘木")

    # 3. 十神 —— 甲日主对照十干全表
    expected_shi_shen = {
        "甲": "比肩", "乙": "劫财", "丙": "食神", "丁": "伤官", "戊": "偏财",
        "己": "正财", "庚": "七杀", "辛": "正官", "壬": "偏印", "癸": "正印",
    }
    for gan, want in expected_shi_shen.items():
        check(f"甲日见{gan}", ten_god("甲", gan), want)

    # 癸日主（1986-05-29 实例：年干丙、月干癸、日干癸、时干壬）
    check("癸日见丙", ten_god("癸", "丙"), "正财")
    check("癸日见癸", ten_god("癸", "癸"), "比肩")
    check("癸日见壬", ten_god("癸", "壬"), "劫财")

    # 4. 藏干 —— 十二支全表本气
    expected_main = {
        "子": "癸", "丑": "己", "寅": "甲", "卯": "乙", "辰": "戊", "巳": "丙",
        "午": "丁", "未": "己", "申": "庚", "酉": "辛", "戌": "戊", "亥": "壬",
    }
    for zhi, want in expected_main.items():
        check(f"{zhi}本气", HIDDEN[zhi][0][0], want)

    # 藏干权重：本气 > 中气 > 余气
    for zhi, stems in HIDDEN.items():
        check(f"{zhi}藏干数", len(stems) in (1, 2, 3), True)
        weights = [w for _, _, w in stems]
        check(f"{zhi}权重递减", weights == sorted(weights, reverse=True), True)

    # 5. 十二长生 —— 关键锚点
    check("甲在亥", growth_phase("甲", "亥"), "长生")
    check("甲在卯", growth_phase("甲", "卯"), "帝旺")
    check("甲在寅", growth_phase("甲", "寅"), "临官")
    check("甲在午", growth_phase("甲", "午"), "死")
    check("乙在寅", growth_phase("乙", "寅"), "帝旺")
    check("乙在午", growth_phase("乙", "午"), "长生")
    check("丙在午", growth_phase("丙", "午"), "帝旺")
    check("丙在寅", growth_phase("丙", "寅"), "长生")
    check("庚在酉", growth_phase("庚", "酉"), "帝旺")
    check("癸在子", growth_phase("癸", "子"), "临官")
    check("辛在酉", growth_phase("辛", "酉"), "临官")
    check("壬在申", growth_phase("壬", "申"), "长生")

    # 6. 空亡
    check("甲子旬空", void_branches("甲子"), ["戌", "亥"])
    check("癸酉空亡", void_branches("癸酉"), ["戌", "亥"])
    check("甲戌旬空", void_branches("甲戌"), ["申", "酉"])
    check("庚午空亡", void_branches("庚午"), ["戌", "亥"])

    # 7. 五行生克闭环
    for wx in ["木", "火", "土", "金", "水"]:
        check(f"{wx}生克不同源", GENERATES[wx] != OVERCOMES[wx], True)

    # 8. 完整命例：1986-05-29 子时
    case = {"年": "丙寅", "月": "癸巳", "日": "癸酉", "时": "壬子"}
    check("命例纳音串", " ".join(NA_YIN[ganzhi_index(v) // 2] for v in case.values()),
          "炉中火 长流水 剑锋金 桑柘木")
    day_master = "癸"
    check("命例年干十神", ten_god(day_master, "丙"), "正财")
    check("命例月干十神", ten_god(day_master, "癸"), "比肩")
    check("命例时干十神", ten_god(day_master, "壬"), "劫财")

    # ---------------------------------------------------------------- 输出
    print(f"共执行 {CHECKS} 项断言")
    if FAILS:
        print(f"失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  x", f)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
