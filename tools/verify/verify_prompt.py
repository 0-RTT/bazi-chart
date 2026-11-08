"""盘面文本与流月规则的静态校验。

分两块：

1. 流月——五虎遁全表、月建干支阴阳必配、某一年逐柱对撞；
2. 盘面文本——任务段八节齐全，数据段四节齐全且不得混进推论项（旺衰、格局、调候、喜用）
   与任务指令、规避语。

文本这一块是机器把关的意义所在：靠人眼守「别把神煞写进去」守不住，
改一次排版就可能带回来，必须让 CI 拦。
"""

import os
import re
import sys

FAILS = []
CHECKS = 0


def check(cond, msg):
    global CHECKS
    CHECKS += 1
    if not cond:
        FAILS.append(msg)


GAN = list("甲乙丙丁戊己庚辛壬癸")
ZHI = list("子丑寅卯辰巳午未申酉戌亥")
JIE_BRANCHES = list("寅卯辰巳午未申酉戌亥子丑")

NAYIN = [
    "海中金", "炉中火", "大林木", "路旁土", "剑锋金", "山头火",
    "涧下水", "城头土", "白蜡金", "杨柳木", "泉中水", "屋上土",
    "霹雳火", "松柏木", "长流水", "沙中金", "山下火", "平地木",
    "壁上土", "金箔金", "覆灯火", "天河水", "大驿土", "钗钏金",
    "桑柘木", "大溪水", "沙中土", "天上火", "石榴木", "大海水",
]

WU_HU = {
    "甲": "丙", "己": "丙", "乙": "戊", "庚": "戊",
    "丙": "庚", "辛": "庚", "丁": "壬", "壬": "壬",
    "戊": "甲", "癸": "甲",
}


CORE = os.path.join("core")


def read(*parts):
    return open(os.path.join(CORE, *parts), encoding="utf-8").read()


def strip_comments(src):
    """去掉 KDoc、块注释与行注释，只留代码与字符串字面量。

    注释里常写着「不写『请分析』」这类说明，若一并扫描会误报。
    """
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    kept = []
    for line in src.splitlines():
        idx = line.find("//")
        kept.append(line[:idx] if idx >= 0 else line)
    return "\n".join(kept)


# ---------------------------------------------------------------- 五虎遁

def first_month_gan(year_gan):
    idx = (GAN.index(year_gan) % 5 * 2 + 2) % 10
    return GAN[idx]


def month_gz(year_gan, month_index):
    g = (GAN.index(first_month_gan(year_gan)) + month_index) % 10
    return GAN[g] + JIE_BRANCHES[month_index]


def year_gan_of(year):
    return GAN[((year - 4) % 60 + 60) % 60 % 10]


def test_wu_hu():
    for yg, expect in WU_HU.items():
        check(first_month_gan(yg) == expect,
              f"五虎遁 {yg} 年正月应为{expect}寅，实得{first_month_gan(yg)}寅")


def test_month_cycle():
    for yg in GAN:
        for i in range(12):
            gz = month_gz(yg, i)
            check(gz[1] == JIE_BRANCHES[i],
                  f"{yg} 年第{i + 1}月支应为{JIE_BRANCHES[i]}，实得{gz[1]}")
            check(GAN.index(gz[0]) % 2 == ZHI.index(gz[1]) % 2,
                  f"{yg} 年第{i + 1}月得{gz}，阴阳不配")


def test_concrete_2026():
    check(year_gan_of(2026) == "丙", f"2026 年干应为丙，实得{year_gan_of(2026)}")
    seq = [month_gz("丙", i) for i in range(12)]
    expect = ["庚寅", "辛卯", "壬辰", "癸巳", "甲午", "乙未",
              "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑"]
    check(seq == expect, f"2026 流月应为{expect}，实得{seq}")


# ---------------------------------------------------------------- 纳音

def test_nayin():
    check(len(NAYIN) == 30, f"纳音应有 30 个，实得{len(NAYIN)}")
    for n in NAYIN:
        check(n[-1] in "木火土金水", f"纳音{n}末字非五行")


# ---------------------------------------------------------------- 古籍与文本

def test_prompt_sections():
    src = strip_comments(read("interpret", "src", "main", "kotlin", "com", "bazi", "interpret", "AiPrompt.kt"))

    # 任务段：九节齐全。这份文本的唯一用途是粘给大模型，要求必须写死在生成端。
    for title in ["# 八字命盘分析", "## 一、先校盘", "## 二、判读主链", "## 三、判读纪律",
                  "## 四、分析维度", "## 五、关键事件", "## 六、建议要能执行",
                  "## 七、流月只在落到月份时用", "## 八、不确定就说不确定",
                  "## 九、输出分两段"]:
        check(title in src, f"AiPrompt.kt 缺任务章节：{title}")

    # 六步主链。少了次序，模型就会把维度当清单逐条填写，
    # 写出来的东西样样都提、样样不能核对——这是上一版最大的毛病。
    for step in ["月令取格", "论成败救应", "取用神", "看刑冲合害", "落宫位", "岁运引动"]:
        check(step in src, f"AiPrompt.kt 主链缺一步：{step}")
    check("先看提纲为重" in src, "主链未引《渊海子平》次序")
    check("八字用神，专求月令" in src, "主链未引《子平真诠》")

    # 任务段必须点明的要素：八维度（含性格）、事件四项、判读纪律、分档输出
    for word in ["性格", "事业", "财运", "婚恋", "子女", "六亲", "健康", "学业"]:
        check(word in src, f"AiPrompt.kt 任务段缺维度：{word}")
    for word in ["时间", "吉凶", "程度", "决定性"]:
        check(word in src, f"AiPrompt.kt 任务段缺事件要素：{word}")
    for word in ["置信度", "多路闭环", "技术有利不等于必然发生", "不用现实结果反推",
                 "术语要落地", "神煞只作旁证", "不要复述盘面"]:
        check(word in src, f"AiPrompt.kt 任务段缺纪律：{word}")
    for word in ["第一段", "第二段", "要不要展开"]:
        check(word in src, f"AiPrompt.kt 任务段缺分档输出：{word}")

    # 数据段五节（结构因子是这一版新增的可核对面）
    for title in ["## 排盘", "## 结构因子", "## 大运", "## 流年", "## 流月"]:
        check(title in src, f"AiPrompt.kt 缺数据章节：{title}")

    # 因子表逐行。这些行是「可复核」的载体：模型判错时，读者拿它们一算就知道错在哪。
    # 「天干帮身」与「帮身力量占比」必须分列：前者只数四天干，后者含地支藏干加权，
    # 挤在一行会被读成「这 N 位贡献了 X%」。
    for row in ["月令", "日主在月令", "得令", "得地", "天干帮身", "帮身力量占比",
                "五行力量", "四柱未见", "天干关系", "地支关系", "月令气候"]:
        check(("| " + row + " |") in src, f"结构因子缺行：{row}")

    head, sep, data = src.partition("以下为盘面数据。")
    check(sep != "" and data.strip() != "", "AiPrompt.kt 缺「以下为盘面数据」分隔")

    # 用户决定：不加免责声明。钉住它，免得往后「顺手补一句」又把篇幅撑长。
    # 只查任务段的**输出字面量**：KDoc 里正写着「按用户决定不加免责声明」，
    # 查源码会把注释本身当成违规。
    task_text = "".join(re.findall(r'"([^"\n]*)"', head))
    check(len(task_text) > 500, "任务段字面量提取异常，可能是切分点变了")
    for word in ["免责", "仅供", "娱乐参考", "非专业决策", "责任自负", "不构成"]:
        check(word not in task_text, f"按用户决定不加免责声明，任务段却出现：{word}")

    # 数据段只陈述事实：旺衰、格局、调候、喜用、古籍一律不得出现。
    # 只查**输出文本**（appendLine 的字面量），不查函数源码——
    # appendFactors 的 KDoc 里正写着「不给格局、用神」，查源码会误判。
    data_text = "".join(re.findall(r'"([^"\n]*)"', data))
    check(len(data_text) > 500, "数据段字面量提取异常，可能是切分点变了")
    for word in ["旺衰", "格局", "调候", "古籍", "喜用", "用神"]:
        check(word not in data_text, f"数据段不应含推论项：{word}")

    # 神煞作为一行事实进数据段，但不是单独成节
    check('"| 神煞 | "' in data, "数据段缺神煞行")
    check('it != "空亡"' in data, "神煞行未排除重复的空亡")

    # 规避语一律不得出现（模型怎么答是模型的事，本应用不替它划定边界）
    for word in ["仅供参考", "不构成", "不作为", "不可作为", "风险提示",
                 "仅供传统文化研究"]:
        check(word not in src, f"AiPrompt.kt 出现规避语：{word}")

    # 依赖边界：本层只收字符串，不依赖分析结果
    check("analysis: AnalysisResult" not in src, "AiPrompt 不应再依赖 AnalysisResult")
    check("ShenShaAnalyzer" not in src, "AiPrompt 不应再引用神煞分析器")
    check("shenSha: Map<PillarPosition, List<String>>" in src, "AiPrompt 应通过字符串入参接收神煞")


def test_stem_relations():
    """天干五合与相冲：引擎只判有没有合、合化何物，不判化与不化。"""
    tables = read("bazi", "src", "main", "kotlin", "com", "bazi", "engine", "Tables.kt")
    check("object StemRelations" in tables, "Tables.kt 缺天干关系表")
    check("data class StemRelation" in tables, "Tables.kt 缺 StemRelation 数据类")

    # 五合五组：合化五行各自唯一
    for pair, element in [("JIA", "TU"), ("YI", "JIN"), ("BING", "SHUI"),
                          ("DING", "MU"), ("WU", "HUO")]:
        check(("TianGan.%s to (" % pair) in tables, f"五合缺 %s 一组" % pair)
        check(("WuXing.%s)" % element) in tables, f"五合 %s 的合化五行缺失" % pair)

    # 五合十干全部成对出现（正反各一次）
    for gan in ["JIA", "YI", "BING", "DING", "WU", "JI", "GENG", "XIN", "REN", "GUI"]:
        check(("TianGan.%s to (TianGan." % gan) in tables, f"五合缺天干 {gan}")

    # 相冲四组，且戊己不入冲
    section = tables[tables.index("object StemRelations"):]
    check(section.count("to TianGan.") >= 8, "相冲对不足四组（正反共八条）")
    check("TianGan.WU to TianGan." not in section.replace("TianGan.WU to (TianGan.", ""),
          "戊不应入相冲")
    check("TianGan.JI to TianGan." not in section.replace("TianGan.JI to (TianGan.", ""),
          "己不应入相冲")
    check("fun between(" in section, "缺全盘枚举 between()")


def test_factors_wiring():
    """结构因子必须由 app 层从分析层取出后传入，core:interpret 不依赖 core:analysis。"""
    vm = open(os.path.join("app", "src", "main", "java", "com", "bazi", "app",
                           "BaziViewModel.kt"), encoding="utf-8").read()
    check("structureFactors(" in vm, "ViewModel 未组装结构因子")
    check("StructureFactors(" in vm, "ViewModel 未构造 StructureFactors")
    check("GrowthPhaseTable.of(" in vm, "因子缺日主在月令的十二长生")
    check("HiddenStemTable.mainStem(" in vm, "因子缺月令本气")
    check("StemRelations.between(" in vm, "因子缺天干五合／相冲")
    check("elementScores" in vm, "因子缺五行力量")

    prompt = read("interpret", "src", "main", "kotlin", "com", "bazi", "interpret", "AiPrompt.kt")
    check("data class StructureFactors" in prompt, "AiPrompt.kt 缺 StructureFactors")
    check("factors: StructureFactors? = null" in prompt, "build() 未接受因子")
    check("import com.bazi.analysis" not in prompt, "core:interpret 不得依赖 core:analysis")


def test_interpreter_removed():
    root = os.path.join("core", "interpret", "src", "main", "kotlin", "com", "bazi", "interpret")
    for gone in ["Interpreter.kt", "Classics.kt", "TextBank.kt"]:
        check(not os.path.exists(os.path.join(root, gone)), f"断语实现应已删除：{gone}")

    def read_app(*parts):
        return open(os.path.join("app", "src", "main", "java", "com", "bazi", "app", *parts),
                    encoding="utf-8").read()

    chart = read_app("ui", "ChartScreen.kt")
    check("InterpretTab" not in chart, "命盘页仍挂着断语页签")
    check('"断语"' not in chart, "页签仍含「断语」")

    vm = read_app("BaziViewModel.kt")
    check("interpretation" not in vm, "ViewModel 仍生成断语")

    analyzer = read("analysis", "src", "main", "kotlin", "com", "bazi", "analysis", "Analyzer.kt")
    check("overcomeBy()" in analyzer, "Analyzer.favorableElements 未修正官杀取法")


def test_interpreter_has_classics():
    src = read("interpret", "src", "main", "kotlin", "com", "bazi", "interpret", "Interpreter.kt")
    check("classicsSection" in src, "Interpreter 未接入古籍节")
    check("古籍参照" in src, "断语缺「古籍参照」标题")
    check("ClassicReader.read" in src, "断语未调用 ClassicReader")
    check("divergences(" not in src, "断语不应再调用分歧对照")
    check("yongShen" in src, "Interpreter 未接入取用取舍章节")

    analyzer = read("analysis", "src", "main", "kotlin", "com", "bazi", "analysis", "Analyzer.kt")
    check("overcomeBy()" in analyzer, "Analyzer.favorableElements 未修正官杀取法")


# ---------------------------------------------------------------- 关系扫描

def test_relations_source():
    src = read("bazi", "src", "main", "kotlin", "com", "bazi", "engine", "Tables.kt")
    check("Triple(DiZhi.YIN, DiZhi.SI, DiZhi.SHEN)" in src, "缺寅巳申三刑组")
    check("Triple(DiZhi.CHOU, DiZhi.XU, DiZhi.WEI)" in src, "缺丑戌未三刑组")
    check("DiZhi.ZI, DiZhi.MAO" in src, "缺子卯相刑")
    check("SELF_XING" in src, "缺自刑")
    check("it.size == 2" not in src, "旧的「数组长度等于2」判定仍在，三刑会被漏扫")
    check("BAN_HE" in src, "缺半合")
    check("SAN_XING.any" in src, "三刑组内两两判定缺失")


def test_lunar_day_text():
    src = read("calendar", "src", "main", "kotlin", "com", "bazi", "calendar", "LunarText.kt")
    for token in ['"初十"', '"二十"', '"三十"', '"廿"', '"初"']:
        check(token in src, f"LunarText.kt 缺写法：{token}")
    ui = open(os.path.join("app", "src", "main", "java", "com", "bazi", "app", "ui",
                           "InputScreen.kt"), encoding="utf-8").read()
    check("LunarText.dayName(it)" in ui, "录入页农历日列未改用中文日名")
    check('(1..state.dayCount).map { "$it" }' in ui, "录入页公历日列应保留阿拉伯数字")


def main():
    os.chdir(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    test_wu_hu()
    test_month_cycle()
    test_concrete_2026()
    test_nayin()
    test_prompt_sections()
    test_stem_relations()
    test_factors_wiring()
    test_interpreter_removed()
    test_relations_source()
    test_lunar_day_text()

    print(f"共执行 {CHECKS} 项断言")
    print("  五虎遁 10 组、月建 120 组、2026 丙午年 12 柱")
    print("  纳音 30 条、任务段九节与六步主链、数据段五节纯净度、天干五合相冲、"
          "结构因子接线、断语已下线、三刑补全、农历日名")
    if FAILS:
        print(f"\n失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  X", f)
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
