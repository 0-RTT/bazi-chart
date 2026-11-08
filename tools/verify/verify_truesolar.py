"""
真太阳时校正校验。

验证两件事：
1. 均时差（EoT）的数值量级是否落在 ±16 分钟内，并与已知极值日期吻合；
2. 极端经度（乌鲁木齐 87.62°E）下，钟表时间与真太阳时是否跨时辰——
   这是排盘软件最常见的错误来源。

运行：python tools/verify/verify_truesolar.py
"""

from __future__ import annotations

import math
import sys

DI_ZHI = ["子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"]

FAILS: list[str] = []
CHECKS = 0


def check(label, actual, expected, tol=None):
    global CHECKS
    CHECKS += 1
    ok = abs(actual - expected) <= tol if tol is not None else actual == expected
    if not ok:
        FAILS.append(f"{label}: 期望 {expected}，实际 {actual}")


def equation_of_time(day_of_year: int, hour: int = 12) -> float:
    """NOAA 近似式，返回分钟。"""
    gamma = 2.0 * math.pi / 365.0 * (day_of_year - 1 + (hour - 12) / 24.0)
    return 229.18 * (
        0.000075
        + 0.001868 * math.cos(gamma)
        - 0.032077 * math.sin(gamma)
        - 0.014615 * math.cos(2 * gamma)
        - 0.040849 * math.sin(2 * gamma)
    )


def hour_index(hour: int) -> int:
    return ((hour + 1) // 2) % 12


def correct(hour: int, minute: int, longitude: float, day_of_year: int):
    lon_min = (longitude - 120.0) * 4.0
    eot = equation_of_time(day_of_year, hour)
    total = lon_min + eot
    total_minutes = hour * 60 + minute + round(total)
    total_minutes %= 1440
    return total_minutes // 60, total_minutes % 60, lon_min, eot


def main() -> int:
    # 1. 均时差极值：11 月初约 +16.4 分钟，2 月中约 -14.2 分钟
    eot_nov = equation_of_time(307)
    eot_feb = equation_of_time(45)
    check("11月初均时差接近正极大", abs(eot_nov - 16.4) < 1.5, True)
    check("2月中均时差接近负极大", abs(eot_feb + 14.2) < 1.5, True)
    check("均时差落在 ±17 分钟内", abs(equation_of_time(200)) < 17.0, True)

    # 2. 北京（116.41°E）2024-02-04 12:00
    h, m, lon_min, eot = correct(12, 0, 116.41, 35)
    check("北京经度修正", round(lon_min, 2), -14.36, 0.01)
    check("北京真太阳时跨入午时", DI_ZHI[hour_index(h)], "午")

    # 3. 乌鲁木齐（87.62°E）2024-02-04 12:00 —— 应跨到巳时
    h2, m2, lon2, eot2 = correct(12, 0, 87.62, 35)
    check("乌鲁木齐经度修正", round(lon2, 2), -129.52, 0.01)
    check("乌鲁木齐真太阳时时刻", f"{h2:02d}:{m2:02d}", "09:37")
    check("乌鲁木齐钟表午时→真太阳时巳时",
          (DI_ZHI[hour_index(12)], DI_ZHI[hour_index(h2)]), ("午", "巳"))

    # 4. 经度差 1° 应折 4 分钟
    check("每度 4 分钟", round((121.0 - 120.0) * 4.0, 2), 4.0, 0.001)

    # 5. 时辰边界：23:00 与 00:59 同属子时
    check("23 时归子", DI_ZHI[hour_index(23)], "子")
    check("0 时归子", DI_ZHI[hour_index(0)], "子")
    check("1 时归丑", DI_ZHI[hour_index(1)], "丑")
    check("2 时归丑", DI_ZHI[hour_index(2)], "丑")
    check("3 时归寅", DI_ZHI[hour_index(3)], "寅")
    check("22 时归亥", DI_ZHI[hour_index(22)], "亥")

    print(f"共执行 {CHECKS} 项断言")
    print(f"  北京 116.41°E 12:00 → 真太阳时 {h:02d}:{m:02d}（经度{lon_min:+.2f}分 均时差{eot:+.2f}分）")
    print(f"  乌鲁木齐 87.62°E 12:00 → 真太阳时 {h2:02d}:{m2:02d}（经度{lon2:+.2f}分 均时差{eot2:+.2f}分）")
    if FAILS:
        print(f"失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  x", f)
        return 1
    print("全部通过")
    return 0


if __name__ == "__main__":
    sys.exit(main())
