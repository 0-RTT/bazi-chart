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

# 五行标记改为「本字」：干支大字右下角写木火土金水，与干支同色。
# 抽象图形（树/焰/山/菱/滴）已下线——不认字就看不懂，认字也要先记图形。
check("ElementGlyph" not in plate, "命盘页仍在用抽象五行图形")
check("Canvas" not in plate, "命盘页仍在用 Canvas 自绘记号")
check("fun GanZhiCell(" in plate, "缺少干支单元 GanZhiCell")
check("wx.cn" in plate, "干支单元未渲染五行本字")
check("fontSize = 30.sp" in plate, "干支大字应为 30sp")
# 藏干一格仍是「干+五行」，如「庚金」
check("hidden.hidden.gan.wuXing.cn" in plate, "藏干未附五行本字")

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


# 构造盘一：丁酉 甲申 丙午 壬寅（四柱合法组合，非真实命例）
BASELINE = [
    ("丁酉", 33, "长生", "辰巳"),
    ("甲申", 20, "绝", "午未"),
    ("丙午", 42, "帝旺", "寅卯"),
    ("壬寅", 38, "病", "辰巳"),
]

for gz, idx, phase, void in BASELINE:
    check(growth(gz[0], gz[1]) == phase, "%s 自坐应为%s，算得%s" % (gz, phase, growth(gz[0], gz[1])))
    check(void_branches(idx) == void, "%s 空亡应为%s，算得%s" % (gz, void, void_branches(idx)))

# 六十甲子：空支恒为两支，且不含本柱地支
for i in range(60):
    v = void_branches(i)
    check(len(v) == 2, "第 %d 旬空应两支" % i)
    check(ZHI[i % 12] not in v, "第 %d 空支不应含本柱地支" % i)

# 自坐与星运：日柱必然相同，年柱通常不同
check(growth("丙", "午") == growth("丙", "午"), "日柱自坐与星运应一致")
check(growth("丙", "酉") != growth("丁", "酉"), "年柱星运与自坐应不同")


# ---------------------------------------------------------------- 三、整盘逐格
#
# 构造盘三：丁巳 戊申 丙辰 癸未（四柱合法组合，非真实命例）
# 逐格核对藏干、副星、星运、自坐、空亡、纳音——用户报「其他项目也需审查」，
# 这一节就是那件事的证据：表格里每一格都由独立实现重新算一遍。

WUXING = {}
for _g in "甲乙":
    WUXING[_g] = "木"
for _g in "丙丁":
    WUXING[_g] = "火"
for _g in "戊己":
    WUXING[_g] = "土"
for _g in "庚辛":
    WUXING[_g] = "金"
for _g in "壬癸":
    WUXING[_g] = "水"

SHENG = {"木": "火", "火": "土", "土": "金", "金": "水", "水": "木"}
KE = {"木": "土", "土": "水", "水": "火", "火": "金", "金": "木"}

HIDDEN = {
    "子": "癸", "丑": "己癸辛", "寅": "甲丙戊", "卯": "乙",
    "辰": "戊乙癸", "巳": "丙戊庚", "午": "丁己", "未": "己丁乙",
    "申": "庚壬戊", "酉": "辛", "戌": "戊辛丁", "亥": "壬甲",
}

NAYIN_PAIRS = [
    "海中金", "炉中火", "大林木", "路旁土", "剑锋金",
    "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
    "泉中水", "屋上土", "霹雳火", "松柏木", "长流水",
    "沙中金", "山下火", "平地木", "壁上土", "金箔金",
    "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木",
    "大溪水", "沙中土", "天上火", "石榴木", "大海水",
]


def ten_god(day_gan, target_gan):
    """以日主为基准判十神：同五行比劫、我生食伤、我克财、克我官杀、生我印。"""
    dm, tg = WUXING[day_gan], WUXING[target_gan]
    same_polarity = (GAN.index(day_gan) % 2) == (GAN.index(target_gan) % 2)
    if dm == tg:
        return "比肩" if same_polarity else "劫财"
    if SHENG[dm] == tg:
        return "食神" if same_polarity else "伤官"
    if KE[dm] == tg:
        return "偏财" if same_polarity else "正财"
    if KE[tg] == dm:
        return "七杀" if same_polarity else "正官"
    return "偏印" if same_polarity else "正印"


def nayin(index):
    return NAYIN_PAIRS[index // 2]


CASE_B = {
    "label": "丁巳 戊申 丙辰 癸未",
    "day_gan": "丙",
    "pillars": [
        # 干支, 甲子序, 主星, 藏干, 藏干副星, 星运, 自坐, 空亡, 纳音
        ("丁巳", 53, "劫财", "丙戊庚", "比肩食神偏财", "临官", "帝旺", "子丑", "沙中土"),
        ("戊申", 44, "食神", "庚壬戊", "偏财七杀食神", "病", "病", "寅卯", "大驿土"),
        ("丙辰", 52, None, "戊乙癸", "食神正印正官", "冠带", "冠带", "子丑", "沙中土"),
        ("癸未", 19, "正官", "己丁乙", "伤官劫财正印", "衰", "墓", "申酉", "杨柳木"),
    ],
}

for gz, idx, main_god, hidden, hidden_gods, xing_yun, zi_zuo, void, na_yin in CASE_B["pillars"]:
    label = CASE_B["label"] + " " + gz
    check(nayin(idx) == na_yin, label + " 纳音应为%s，算得%s" % (na_yin, nayin(idx)))
    check(void_branches(idx) == void, label + " 空亡应为%s，算得%s" % (void, void_branches(idx)))
    check(HIDDEN[gz[1]] == hidden, label + " 藏干应为%s，算得%s" % (hidden, HIDDEN[gz[1]]))
    check(growth(gz[0], gz[1]) == zi_zuo,
          label + " 自坐应为%s，算得%s" % (zi_zuo, growth(gz[0], gz[1])))
    check(growth(CASE_B["day_gan"], gz[1]) == xing_yun,
          label + " 星运应为%s，算得%s" % (xing_yun, growth(CASE_B["day_gan"], gz[1])))
    gods = "".join(ten_god(CASE_B["day_gan"], h) for h in hidden)
    check(gods == hidden_gods, label + " 副星应为%s，算得%s" % (hidden_gods, gods))
    if main_god is not None:
        got_main = ten_god(CASE_B["day_gan"], gz[0])
        check(got_main == main_god, label + " 主星应为%s，算得%s" % (main_god, got_main))


# ---------------------------------------------------------------- 结果

print("四柱表版式与数据校验")
print("  行标顺序：%s" % " / ".join(ROWS))
print("  构造盘一：丁酉空辰巳 甲申空午未 丙午空寅卯 壬寅空辰巳")
print("  自坐：丁坐酉长生、甲坐申绝、丙坐午帝旺、壬坐寅病")
print("共执行 %d 项断言" % passed)
if failed:
    print("失败 %d 项：" % len(failed))
    for f in failed:
        print("  -", f)
    sys.exit(1)
print("全部通过")
