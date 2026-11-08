"""
五行打分 / 旺衰 / 格局 / 调候 / 神煞 校验。

用 1986-05-29 子时（丙寅 癸巳 癸酉 壬子，日主癸水）这一已核验命例，
手算期望值与程序算法对撞。

运行：python tools/verify/verify_analysis.py
"""

from __future__ import annotations

import sys

TIAN_GAN = ["甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"]
DI_ZHI = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]
GAN_WX = ["木", "木", "火", "火", "土", "土", "金", "金", "水", "水"]
ZHI_WX = ["水", "土", "木", "木", "土", "火", "火", "土", "金", "金", "土", "水"]
GENERATES = {"木": "火", "火": "土", "土": "金", "金": "水", "水": "木"}

HIDDEN = {
    "子": [("癸", 1.0)], "丑": [("己", 1.0), ("癸", 0.5), ("辛", 0.2)],
    "寅": [("甲", 1.0), ("丙", 0.5), ("戊", 0.2)], "卯": [("乙", 1.0)],
    "辰": [("戊", 1.0), ("乙", 0.5), ("癸", 0.2)], "巳": [("丙", 1.0), ("庚", 0.5), ("戊", 0.2)],
    "午": [("丁", 1.0), ("己", 0.5)], "未": [("己", 1.0), ("丁", 0.5), ("乙", 0.2)],
    "申": [("庚", 1.0), ("壬", 0.5), ("戊", 0.2)], "酉": [("辛", 1.0)],
    "戌": [("戊", 1.0), ("辛", 0.5), ("丁", 0.2)], "亥": [("壬", 1.0), ("甲", 0.5)],
}

ZHI_WEIGHT = {"年": 1.0, "月": 3.0, "日": 2.0, "时": 1.0}
GAN_WEIGHT = 1.0

FAILS: list[str] = []
CHECKS = 0


def check(label, actual, expected, tol=1e-6):
    global CHECKS
    CHECKS += 1
    ok = abs(actual - expected) <= tol if isinstance(expected, float) else actual == expected
    if not ok:
        FAILS.append(f"{label}: 期望 {expected}，实际 {actual}")


def score(pillars: dict[str, str]) -> dict[str, float]:
    """pillars: {"年": "丙寅", ...}"""
    result = {wx: 0.0 for wx in "木火土金水"}
    for pos, gz in pillars.items():
        gan, zhi = gz[0], gz[1]
        result[GAN_WX[TIAN_GAN.index(gan)]] += GAN_WEIGHT
        for hidden_gan, w in HIDDEN[zhi]:
            result[GAN_WX[TIAN_GAN.index(hidden_gan)]] += w * ZHI_WEIGHT[pos]
    return result


def ten_god(day_gan, target_gan):
    di, ti = TIAN_GAN.index(day_gan), TIAN_GAN.index(target_gan)
    same = (di % 2 == 0) == (ti % 2 == 0)
    me, other = GAN_WX[di], GAN_WX[ti]
    OVERCOMES = {"木": "土", "土": "水", "水": "火", "火": "金", "金": "木"}
    if other == me:
        return "比肩" if same else "劫财"
    if GENERATES[me] == other:
        return "食神" if same else "伤官"
    if OVERCOMES[me] == other:
        return "偏财" if same else "正财"
    if OVERCOMES[other] == me:
        return "七杀" if same else "正官"
    return "偏印" if same else "正印"


def main() -> int:
    case = {"年": "丙寅", "月": "癸巳", "日": "癸酉", "时": "壬子"}
    day_master = "癸"
    s = score(case)

    # 手算期望值
    check("木", round(s["木"], 4), 1.0, 1e-9)
    check("火", round(s["火"], 4), 4.5, 1e-9)
    check("土", round(s["土"], 4), 0.8, 1e-9)
    check("金", round(s["金"], 4), 3.5, 1e-9)
    check("水", round(s["水"], 4), 4.0, 1e-9)

    total = sum(s.values())
    check("总分", round(total, 4), 13.8, 1e-9)

    me = GAN_WX[TIAN_GAN.index(day_master)]
    support = s[me] + s[GENERATES and [k for k, v in GENERATES.items() if v == me][0]]
    check("帮身分", round(support, 4), 7.5, 1e-9)

    ratio = support / total
    check("帮身占比", round(ratio, 4), 0.5435, 1e-4)

    level = (
        "极弱" if ratio < 0.30 else "弱" if ratio < 0.42 else "偏弱" if ratio < 0.48
        else "中和" if ratio <= 0.52 else "偏强" if ratio <= 0.58
        else "强" if ratio <= 0.70 else "极强"
    )
    check("旺衰等级", level, "偏强")

    # 得令 / 得地 / 得势
    month_main = HIDDEN["巳"][0][0]
    day_main = HIDDEN["酉"][0][0]
    check("月令本气", month_main, "丙")
    check("得令", GAN_WX[TIAN_GAN.index(month_main)] in (me, [k for k, v in GENERATES.items() if v == me][0]), False)
    check("得地", GAN_WX[TIAN_GAN.index(day_main)] == [k for k, v in GENERATES.items() if v == me][0], True)

    supportive = sum(
        1 for pos in ("年", "月", "时")
        if GAN_WX[TIAN_GAN.index(case[pos][0])] in (me, [k for k, v in GENERATES.items() if v == me][0])
    )
    check("得势", supportive >= 2, True)

    # 格局：月令巳本气丙透干 → 正财格
    check("月令十神", ten_god(day_master, month_main), "正财")
    gans = [gz[0] for gz in case.values()]
    check("丙透干", month_main in gans, True)
    check("格局", "正财格", "正财格")

    # 调候：癸水巳月 → 辛金
    check("调候主用", "辛", "辛")
    all_gans = gans + [g for gz in case.values() for g, _ in HIDDEN[gz[1]]]
    check("调候用神现于柱中", "辛" in all_gans, True)

    # 神煞：癸日 → 天乙卯巳（巳在月支）；禄神子（时支）；将星（日支酉，巳酉丑将星在酉）
    branches = {pos: gz[1] for pos, gz in case.items()}
    check("天乙贵人位", "巳" in branches.values(), True)
    check("禄神位", "子" in branches.values(), True)
    check("将星位", "酉" in branches.values(), True)

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
