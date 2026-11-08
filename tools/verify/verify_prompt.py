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

    # 任务段：八节齐全。这份文本的唯一用途是粘给大模型，要求必须写死在生成端。
    for title in ["# 八字命盘分析", "## 一、先校盘", "## 二、分析方法", "## 三、分析维度",
                  "## 四、关键事件", "## 五、结论要能核对", "## 六、建议要能执行",
                  "## 七、不确定就说不确定", "## 八、输出格式"]:
        check(title in src, f"AiPrompt.kt 缺任务章节：{title}")

    # 任务段必须点明的要素：七个维度、事件四项、五派交叉
    for word in ["事业", "财运", "婚恋", "子女", "六亲", "健康", "学业",
                 "时间范围", "吉凶属性", "影响程度", "决定性",
                 "子平格局派", "旺衰扶抑派", "调候派", "盲派", "神煞：只作旁证"]:
        check(word in src, f"AiPrompt.kt 任务段缺要素：{word}")

    # 数据段四节
    for title in ["## 排盘", "## 大运", "## 流年", "## 流月"]:
        check(title in src, f"AiPrompt.kt 缺数据章节：{title}")

    head, sep, data = src.partition("以下为盘面数据。")
    check(sep != "" and data.strip() != "", "AiPrompt.kt 缺「以下为盘面数据」分隔")

    # 数据段只陈述事实：旺衰、格局、调候、喜用、古籍一律不得出现
    for word in ["旺衰", "格局", "调候", "古籍", "喜用", "用神"]:
        check(word not in data, f"数据段不应含推论项：{word}")

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
    test_interpreter_removed()
    test_relations_source()
    test_lunar_day_text()

    print(f"共执行 {CHECKS} 项断言")
    print("  五虎遁 10 组、月建 120 组、2026 丙午年 12 柱")
    print("  纳音 30 条、任务段八节、数据段四节纯净度、断语已下线、三刑补全、农历日名")
    if FAILS:
        print(f"\n失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  X", f)
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
