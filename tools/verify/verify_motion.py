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
for key, ms in (("stateMillis", 220), ("swapMillis", 180), ("pageMillis", 260)):
    check(("const val %s = %d" % (key, ms)) in components,
          "Motion.%s 应为 %d" % (key, ms))
for line in ("val state = tween<Color>(durationMillis = stateMillis",
             "val swap = tween<Float>(durationMillis = swapMillis",
             "val page = tween<Float>(durationMillis = pageMillis",
             "val reveal = tween<Float>(durationMillis = stateMillis"):
    check(line in components, "Motion 缺规格：" + line)
check("tween<Dp>(durationMillis = stateMillis" in components,
      "Motion 缺 Dp 过渡（边框粗细用）")

# 各处引用口径，而不是各写 tween(...)
for name, src in (("FortuneScreen", fortune), ("ChartScreen", chart)):
    check("tween" not in src, "%s 里硬编码了 tween，应改用 Motion 口径" % name)
# 路由的横向位移必须用 IntOffset 的 spec，只能按时长常量重建——
# 但重建时不许写死数字。
check("tween<IntOffset>(Motion.pageMillis)" in main,
      "路由位移应按 Motion.pageMillis 重建 spec")
check(re.search(r"tween<IntOffset>\(\s*\d+", main) is None,
      "路由位移里写死了时长，应取 Motion.pageMillis")
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

# Pager 引入的新误触：LazyRow 滚到尽头后余量冒泡，会变成「整页翻走」。
# 位移与惯性都要拦——只拦位移，快速一划仍然会翻页。
check("HorizontalOnly" in fortune, "大运带未拦横向滚动余量，推到底会翻页")
check("onPostScroll" in fortune, "未拦横向滚动余量（位移）")
check("onPostFling" in fortune, "未拦横向滚动余量（惯性），快划仍会翻页")
check("available.x" in fortune, "拦余量时未区分横纵，纵向会被一起吃掉")

# 页签：三页并排交给 Pager，不许再用「淡入淡出」——
# 两页都是长列表，交叉期间同一帧里能看到两份错位的文字，这就是「动画很怪」的来源。
check("rememberPagerState" in chart, "页签未改用 Pager")
check("HorizontalPager(" in chart, "缺少 HorizontalPager")
check("beyondViewportPageCount = 1" in chart,
      "未预组合相邻页，首次横滑的第一帧会掉帧")
check("animateScrollToPage" in chart, "点击页签未走 Pager 滚动")
check("Motion.page" in chart, "点击页签未使用页面级口径")
check("Crossfade(" not in chart, "页签仍在用交叉淡入（两页会叠影）")

# 底部页签
check("BottomTabs(" in chart, "缺少底部页签栏")
check("navigationBarsPadding" in chart, "底部栏未让出手势导航条")
check("pager.currentPage" in chart, "底部栏未与 Pager 同步")
check("graphicsLayer" in chart, "页签指示条未走绘制层（会触发重排）")
check("indication = null" in chart, "页签仍有涟漪，54dp 高的水波过重")

# 路由
check("AnimatedContent(" in main, "进出命盘未用 AnimatedContent")
check("slideInHorizontally" in main, "进出命盘缺少横向位移，两版式会叠成重影")
check("togetherWith" in main, "路由缺少 transitionSpec")
check("Crossfade" not in main, "进出命盘仍在用交叉淡入")

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

# ------------------------------------------------------------ 四、三级下钻的联动

# 流月跟的是「选中的那一年」。点大运时若不改这一年，流月就停在上一运的年份上，
# 看起来正是「点大运，流月不动」。
check(re.search(r"picked = index[\s\S]{0,600}?pickedYear =", fortune) is not None,
      "点大运时未重置选中年份，流月会停在上一运")
check("luckIndex == index" in fortune, "未按大运筛选流年区间")
check("span.any { it.year == currentYear }" in fortune,
      "换运时未优先落回本年，点「当运」那一格会跳离今年")
check("if (picked != index)" in fortune,
      "重复点同一格会把已选流年冲回运首")

# ------------------------------------------------------------ 五、流月交节

# 流月格上只写「2/4」，切十年一步的大运时几乎不变（该节日的月日多数只差 0–1 天），
# 会被误读成「数据没跟着年份走」。节名与日期必须一起出现。
chart_model = strip_comments(
    read("core", "bazi", "src", "main", "kotlin", "com", "bazi", "engine", "Chart.kt"))
calc = strip_comments(
    read("core", "bazi", "src", "main", "kotlin", "com", "bazi", "engine", "BaziCalculator.kt"))
check("val jieName: String" in chart_model, "MonthlyFortune 缺 jieName")
check("jieName = SolarTerms.JIE_NAMES[i]" in calc, "jieName 未接十二节表")
check("m.jieName" in fortune, "流月格未显示节名，日期会被误读成固定值")
check("m.startTime.monthValue" in fortune, "流月格未显示交节月日")

# ------------------------------------------------------------ 结果

print("界面动效与等高校验")
print("  大运格：定宽 CHIP_WIDTH、底注行定高，十格等高")
print("  动效四档：state/reveal 220ms、swap 180ms、page 260ms，统一 FastOutSlowInEasing")
print("  页签：HorizontalPager 三页并排 + 底部页签栏；路由：淡入 + 横向位移")
print("  交叉淡入：大运详情、流年、流月各一处")
print("  流月：节名与交节日同显（只写日期会被误读成固定值）")
print("共执行 %d 项断言" % passed)
if failed:
    print("失败 %d 项：" % len(failed))
    for f in failed:
        print("  -", f)
    sys.exit(1)
print("全部通过")
