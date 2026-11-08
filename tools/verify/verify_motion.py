"""界面动效与等高的静态校验。

两条都是「看不出来的 bug」：
1. 大运带里当运那一格比邻格高一行——只有真机截图才看得出，代码里读起来很正常，
   因为 `if (current) Text("当运")` 本身没有任何语法问题。
2. 动效散落在各处、各写一套时长——不会报错，但叠加起来就不顺，
   而且往后每加一个控件都会再抄一份。

所以这里只查两件事：**结构是否保证等高**、**动效是否只有一套口径**。
脚本不去跑 Compose，纯读源码做模式断言，在没有 JDK 的环境里也能跑。
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


def read(*parts):
    with open(os.path.join(ROOT, *parts), encoding="utf-8") as f:
        return f.read()


def strip_comments(src):
    """去掉块注释与行注释。

    注释里正写着反例（「早先是 `if (current) { Text(\"当运\") }`」），
    不剥离就会把反例当成现存代码。
    """
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    return src


UI = os.path.join("app", "src", "main", "java", "com", "bazi", "app", "ui")
fortune = strip_comments(read(UI, "FortuneScreen.kt"))
chart = strip_comments(read(UI, "ChartScreen.kt"))
components = strip_comments(read(UI, "Components.kt"))
main = strip_comments(read("app", "src", "main", "java", "com", "bazi", "app", "MainActivity.kt"))
gradle = read("app", "build.gradle.kts")
toml = read("gradle", "libs.versions.toml")


def body(src, name):
    """取 `fun name(` 到下一个顶层 `@Composable` / `fun` 之间的内容。"""
    marker = "fun " + name + "("
    start = src.index(marker)
    rest = src[start + len(marker):]
    m = re.search(r"\n@Composable|\nprivate fun |\nfun ", rest)
    return rest[: m.start()] if m else rest


# ------------------------------------------------------------ 一、大运格等高

chip = body(fortune, "LuckChip")

# 底注那一行必须有固定高度；否则空串的 Text 在某些字号下量出 0 高，
# 十格又不齐。
check("CHIP_FOOT" in fortune, "缺少底注行的固定高度常量 CHIP_FOOT")
check(re.search(r"CHIP_FOOT\s*=\s*\d+\.dp", fortune) is not None,
      "CHIP_FOOT 应是具体的 dp 值")
check("Box(Modifier.height(CHIP_FOOT)" in chip or "Modifier.height(CHIP_FOOT)" in chip,
      "底注行没有用固定高度的 Box 承载，格子会随「当运」有无而变高")

# 「当运」必须始终渲染、只切换文字，不能由 if 决定是否渲染。
check('if (current) "当运" else ""' in chip,
      "「当运」不是「始终渲染、只换文字」，当运那格会比邻格高一行")

# 旧写法回归防线：if (current) { ... Text("当运") ... }
bad = re.search(r'if\s*\(\s*current\s*\)\s*\{[^}]{0,200}?Text\(\s*"当运"', chip, re.S)
check(bad is None, "LuckChip 里仍用 if 决定是否渲染「当运」，格子高度不齐")

# 三处正文（干支由 GanZhiPair 出，另有年龄、起始年、底注三个 Text）
check('"${luck.startAge}–${luck.endAge}"' in chip, "大运格缺年龄行")
check('"${luck.startYear}"' in chip, "大运格缺起始年行")
check(chip.count("Text(") >= 3, "大运格正文行数不足，可能被改掉了")

# 十格同宽：宽度必须来自同一个常量，不能各写各的
check("CHIP_WIDTH" in fortune, "大运格缺统一定宽常量 CHIP_WIDTH")
check(".width(CHIP_WIDTH)" in chip, "大运格未使用统一宽度")
check(len(re.findall(r"\.width\((\d+)\.dp\)", chip)) == 0,
      "大运格里还有硬编码宽度，宽度口径不统一")

# ------------------------------------------------------------ 二、动效口径

# 口径只允许在 Components.kt 的 Motion 里定义
check("internal object Motion" in components, "缺少统一的动效口径 Motion")
check("FastOutSlowInEasing" in components, "Motion 未统一使用 FastOutSlowInEasing")
for key, ms in (("state", 220), ("swap", 180), ("page", 200)):
    pat = r"%s\s*=\s*tween<[A-Za-z]+>\(\s*durationMillis\s*=\s*%d" % (key, ms)
    check(re.search(pat, components) is not None,
          "Motion.%s 应为 %dms 的 tween" % (key, ms))
check(re.search(r"thickness\s*=\s*tween<Dp>", components) is not None,
      "Motion 缺 Dp 过渡（边框粗细用）")

# 各处引用口径，而不是各写 tween(...)
for name, src in (("FortuneScreen", fortune), ("ChartScreen", chart),
                  ("MainActivity", main)):
    check("tween" not in src, "%s 里硬编码了 tween，应改用 Motion 口径" % name)
check(fortune.count("Motion.state") >= 4, "运程页的选中态过渡未统一走 Motion.state")
check(fortune.count("Motion.thickness") >= 2, "运程页的边框粗细过渡未统一走 Motion.thickness")

# 选中态必须是过渡而非翻色
check(chip.count("animateColorAsState") >= 2, "大运格底色与边框未做颜色过渡")
check("animateDpAsState" in chip, "大运格边框粗细未做过渡")
annual = body(fortune, "AnnualCell")
check(annual.count("animateColorAsState") >= 2, "流年格底色与边框未做颜色过渡")
check("animateDpAsState" in annual, "流年格边框粗细未做过渡")
month = body(fortune, "MonthCell")
check(month.count("animateColorAsState") >= 2, "流月格底色与边框未做颜色过渡")

# 三段下钻都要交叉淡入，且各自有可辨识的 label（便于 profiling 时定位）
for label in ("luckDetail", "annualSwap", "monthSwap"):
    check('label = "%s"' % label in fortune, "运程页缺 %s 的交叉淡入" % label)
check(fortune.count("Crossfade(") == 3, "运程页交叉淡入处数与预期不符（应为 3）")

# 大运带自动居中：选中第 7 格时它可能已在屏幕外
check("rememberLazyListState()" in fortune, "大运带未接管滚动状态，无法自动居中")
check("animateScrollToItem" in fortune, "大运带选中后没有滚动到可视区")
check("snapshotFlow" in fortune,
      "大运带的居中未等待首次布局，首帧 scroll 会被静默丢弃")
check("state = luckRow" in fortune, "LazyRow 未接上滚动状态")

# 页签与路由
check("Crossfade(targetState = tab" in chart, "页签切换没有过渡")
check("Motion.page" in chart, "页签切换未使用页面级口径")
check("Crossfade(targetState = onChart" in main, "进出命盘没有过渡")
check("Motion.page" in main, "进出命盘未使用页面级口径")

# 分段选择器
seg = body(components, "Segmented")
check(seg.count("animateColorAsState") >= 2, "分段选择器的选中态仍是瞬跳")

# ------------------------------------------------------------ 三、依赖与结构

check("libs.compose.animation" in gradle,
      "app 未显式声明 compose animation（material3 只带 animation-core）")
check("compose-animation" in toml, "版本目录缺 compose-animation")
check("androidx.compose.animation:animation" in toml, "compose-animation 坐标写错")

# 重写大文件时容易丢组件，这几处是运程页的骨架
for name in ("MonthSection", "MonthCell", "LuckDetailPanel", "StartLuckPanel", "AnnualCell"):
    check("fun " + name + "(" in fortune, "运程页缺少组件 %s" % name)
check("monthliesOf" in fortune, "流月块未接引擎的 monthliesOf")

# ------------------------------------------------------------ 结果

print("界面动效与等高校验")
print("  大运格：定宽 %s、底注行定高，十格等高" % "CHIP_WIDTH")
print("  动效三档：state 220ms / swap 180ms / page 200ms，统一 FastOutSlowInEasing")
print("  交叉淡入：大运详情、流年、流月各一处；页签与路由各一处")
print("共执行 %d 项断言" % passed)
if failed:
    print("失败 %d 项：" % len(failed))
    for f in failed:
        print("  -", f)
    sys.exit(1)
print("全部通过")
