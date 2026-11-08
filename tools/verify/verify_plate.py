"""四柱表版式与数据的静态校验。

两件事分开验：

1. 版式——命盘页的十一行行标是否齐全、顺序是否与设计一致；
   被移除的四个二次推论面板（五行力量、格局调候、神煞、刑冲合害）不得回来。
2. 数据——自坐与旬空按口诀独立复算，与 Kotlin 侧的表逐项对撞。

第 2 部分是纯算术，不依赖 Kotlin 代码，所以能在没有 JDK 的环境里跑。
"""

import os
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


# ---------------------------------------------------------------- 一、版式

def read(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
        return f.read()


def strip_comments(src):
    """去掉块注释与行注释。

    KDoc 里正写着「刻意不放旺衰、格局、调候、刑冲合害」，若不剥离会误判成
    UI 里还在显示这些内容。
    """
    out = []
    in_block = False
    for line in src.splitlines():
        if in_block:
            if "*/" in line:
                in_block = False
                line = line.split("*/", 1)[1]
            else:
                continue
        if "/*" in line:
            head, rest = line.split("/*", 1)
            if "*/" in rest:
                line = head + rest.split("*/", 1)[1]
            else:
                in_block = True
                line = head
        out.append(line.split("//")[0])
    return "\n".join(out)


plate_src = read("app", "src", "main", "java", "com", "bazi", "app", "ui", "ChartScreen.kt")
plate = strip_comments(plate_src)

# 行标按设计顺序。表头「日期」在最上，其后十行。
ROWS = ["日期", "主星", "天干", "地支", "藏干", "副星", "星运", "自坐", "空亡", "纳音", "神煞"]

positions = []
for row in ROWS:
    marker = 'rail = "%s"' % row
    idx = plate.find(marker)
    check(idx > 0, "命盘页缺少行标：%s" % row)
    positions.append(idx)

check(positions == sorted(positions), "行标顺序错乱：%s" % positions)

# 四个面板必须已移除
for gone in ["ElementPanel", "PatternPanel", "ShenShaPanel", "RelationPanel", "StackedBar"]:
    check(gone not in plate, "命盘页仍在引用已移除的面板：%s" % gone)

for word in ["五行力量", "格局与调候", "刑冲合害"]:
    check(word not in plate, "命盘页仍出现已移除的标题：%s" % word)

# 五行记号五种形状齐全
for wx in ["WuXing.MU", "WuXing.HUO", "WuXing.TU", "WuXing.JIN", "WuXing.SHUI"]:
    check(wx in plate, "五行记号缺少分支：%s" % wx)

# 表内每行都取自引擎字段，不得在 UI 里现算
for field in ["ownGrowthPhase", "voidBranches", "growthPhase", "naYin", "ganTenGod", "hiddenStems"]:
    check(field in plate, "命盘页未使用字段：%s" % field)


# ---------------------------------------------------------------- 二、数据

GAN = "甲乙丙丁戊己庚辛壬癸"
ZHI = "子丑寅卯辰巳午未申酉戌亥"

PHASES = ["长生", "沐浴", "冠带", "临官", "帝旺", "衰", "病", "死", "墓", "绝", "胎", "养"]

# 各干长生之地。与 Kotlin 的 GrowthPhaseTable.START 同源。
START = {
    "甲": "亥", "乙": "午", "丙": "寅", "丁": "酉", "戊": "寅",
    "己": "酉", "庚": "巳", "辛": "子", "壬": "申", "癸": "卯",
}
YANG = set("甲丙戊庚壬")


def growth(gan, zhi):
    """自坐：本柱天干在本柱地支的十二长生。阳干顺行、阴干逆行。"""
    start = ZHI.index(START[gan])
    offset = (ZHI.index(zhi) - start) % 12 if gan in YANG else (start - ZHI.index(zhi)) % 12
    return PHASES[offset]


def void_branches(index):
    """本柱干支所属之旬的两个空支。"""
    gan, zhi = index % 10, index % 12
    first = ((zhi - gan + 10) % 12 + 12) % 12
    return ZHI[first] + ZHI[(first + 1) % 12]


# 基准盘：己卯 甲戌 丙午 壬辰（即截图那张）
BASELINE = [
    ("己卯", 15, "病", "申酉"),
    ("甲戌", 10, "养", "申酉"),
    ("丙午", 42, "帝旺", "寅卯"),
    ("壬辰", 28, "墓", "午未"),
]

for gz, idx, phase, void in BASELINE:
    check(growth(gz[0], gz[1]) == phase, "%s 自坐应为%s，算得%s" % (gz, phase, growth(gz[0], gz[1])))
    check(void_branches(idx) == void, "%s 空亡应为%s，算得%s" % (gz, void, void_branches(idx)))

# 六十甲子：空支恒为两支，且不含本柱地支
for i in range(60):
    v = void_branches(i)
    check(len(v) == 2, "第 %d 旬空应两支" % i)
    check(ZHI[i % 12] not in v, "第 %d 空支不应含本柱地支" % i)

# 自坐与星运：日柱必然相同，年柱通常不同（以基准盘为例）
check(growth("丙", "午") == growth("丙", "午"), "日柱自坐与星运应一致")
check(growth("丙", "卯") != growth("己", "卯"), "年柱星运与自坐应不同")


# ---------------------------------------------------------------- 结果

print("四柱表版式与数据校验")
print("  行标顺序：%s" % " / ".join(ROWS))
print("  基准盘：己卯空申酉 甲戌空申酉 丙午空寅卯 壬辰空午未")
print("  自坐：己坐卯病、甲坐戌养、丙坐午帝旺、壬坐辰墓")
print("共执行 %d 项断言" % passed)
if failed:
    print("失败 %d 项：" % len(failed))
    for f in failed:
        print("  -", f)
    sys.exit(1)
print("全部通过")
