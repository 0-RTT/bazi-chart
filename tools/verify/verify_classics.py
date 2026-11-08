"""
古籍层、盘面文本与关系扫描的规则校验。

Kotlin 侧无法在本机构建，所以这里用 Python 把**同一套规则**独立实现一遍，
对撞口诀与全表；再用静态扫描确认：
盘面文本里没有任务指令、没有古籍、没有规避语；古籍在断语里；三刑判定已补全。

覆盖：
1. 五虎遁：十年干 × 十二月建 = 120 组，逐组对撞口诀
2. 六十纳音的末字必为五元素之一（纳音取五行的唯一依据）
3. 十二月建的支序：寅卯辰巳午未申酉戌亥子丑
4. 十部古籍与 Kotlin 枚举逐一对齐
5. AiPrompt 源码：八节齐全、无任务指令、无规避语、无古籍
6. Interpreter 源码：古籍参照节存在、分歧对照已移除
7. Tables.kt：三刑两组、自刑四支、旧的 size==2 判定已清除
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

BOOKS = ["滴天髓", "穷通宝鉴", "三命通会", "八字提要", "子平真诠",
         "天元咸巫", "神峰通考", "千里命稿", "五行精纪", "李虚中命书"]

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

def test_classics_enum():
    src = read("interpret", "src", "main", "kotlin", "com", "bazi", "interpret", "Classics.kt")
    for b in BOOKS:
        check(f'"{b}"' in src, f"Classics.kt 缺古籍：{b}")
    check(src.count("val reading: String") == 1, "ClassicView.reading 定义异常")
    check("DivergenceAxis" not in src, "分歧对照类型应已移除")
    check("fun divergences(" not in src, "分歧对照函数应已移除")


def test_prompt_purity():
    src = strip_comments(read("interpret", "src", "main", "kotlin", "com", "bazi", "interpret", "AiPrompt.kt"))

    for b in ["请完成以下分析", "输出要求", "请你分析", "请分析",
              "仅供参考", "不构成", "不作为", "不可作为", "风险提示",
              "仅供传统文化研究", "你是一位"]:
        check(b not in src, f"AiPrompt.kt 出现不应有的措辞：{b}")

    for b in BOOKS + ["分歧对照", "古籍"]:
        check(b not in src, f"AiPrompt.kt 不应再含：{b}")

    for title in ["一、出生", "二、四柱", "三、四柱神煞", "四、大运",
                  "五、大运神煞", "六、流年", "七、流年神煞", "八、流月"]:
        check(title in src, f"AiPrompt.kt 缺章节：{title}")

    check("九、古籍分歧" not in src, "盘面文本不应有第九节")


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
    test_classics_enum()
    test_prompt_purity()
    test_interpreter_has_classics()
    test_relations_source()
    test_lunar_day_text()

    print(f"共执行 {CHECKS} 项断言")
    print("  五虎遁 10 组、月建 120 组、2026 丙午年 12 柱")
    print("  纳音 30 条、十部古籍、盘面八节纯净度、古籍归属、三刑补全、农历日名")
    if FAILS:
        print(f"\n失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  X", f)
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
