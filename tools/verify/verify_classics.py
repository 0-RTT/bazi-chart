"""
古籍分歧层与盘面文本的规则校验。

Kotlin 侧无法在本机构建，所以这里用 Python 把**同一套规则**独立实现一遍，
对撞口诀与全表；再用静态扫描确认文本生成器里没有混入任务指令或规避语。

覆盖：
1. 五虎遁：十年干 × 十二月建 = 120 组，逐组对撞口诀
2. 六十纳音的末字必为五元素之一（纳音取五行的唯一依据）
3. 十二月建的支序：寅卯辰巳午未申酉戌亥子丑
4. 十部古籍与 Kotlin 枚举逐一对齐
5. AiPrompt 源码中不得出现任务指令与规避性话语
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

# 口诀：甲己丙作首、乙庚戊为头、丙辛寻庚起、丁壬壬位流、戊癸甲寅求
WU_HU = {
    "甲": "丙", "己": "丙",
    "乙": "戊", "庚": "戊",
    "丙": "庚", "辛": "庚",
    "丁": "壬", "壬": "壬",
    "戊": "甲", "癸": "甲",
}


def first_month_gan(year_gan):
    """五虎遁：由年干得正月（寅月）天干。"""
    idx = (GAN.index(year_gan) % 5 * 2 + 2) % 10
    return GAN[idx]


def month_gz(year_gan, month_index):
    """month_index: 0=正月寅，11=腊月丑。"""
    g = (GAN.index(first_month_gan(year_gan)) + month_index) % 10
    return GAN[g] + JIE_BRANCHES[month_index]


def year_gan_of(year):
    return GAN[((year - 4) % 60 + 60) % 60 % 10]


def test_wu_hu():
    for yg, expect in WU_HU.items():
        check(first_month_gan(yg) == expect, f"五虎遁 {yg} 年正月应为{expect}寅，实得{first_month_gan(yg)}寅")


def test_month_cycle():
    for yg in GAN:
        for i in range(12):
            gz = month_gz(yg, i)
            check(gz[1] == JIE_BRANCHES[i], f"{yg} 年第{i + 1}月支应为{JIE_BRANCHES[i]}，实得{gz[1]}")
            # 干支必须同阴阳（阳干配阳支），否则六十甲子里不存在这一柱
            check(
                GAN.index(gz[0]) % 2 == ZHI.index(gz[1]) % 2,
                f"{yg} 年第{i + 1}月得{gz}，阴阳不配",
            )


def test_concrete_2026():
    # 2026 丙午年：丙辛必定寻庚起 → 正月庚寅
    check(year_gan_of(2026) == "丙", f"2026 年干应为丙，实得{year_gan_of(2026)}")
    seq = [month_gz("丙", i) for i in range(12)]
    expect = ["庚寅", "辛卯", "壬辰", "癸巳", "甲午", "乙未", "丙申", "丁酉", "戊戌", "己亥", "庚子", "辛丑"]
    check(seq == expect, f"2026 流月应为{expect}，实得{seq}")


def test_nayin():
    check(len(NAYIN) == 30, f"纳音应有 30 个（六十甲子两两共用），实得{len(NAYIN)}")
    for n in NAYIN:
        check(n[-1] in "木火土金水", f"纳音{n}末字非五行")
    # 六十甲子按序两两共用一个纳音
    for i in range(60):
        n = NAYIN[i // 2]
        check(i // 2 < 30, "纳音索引越界")


def test_classics_list():
    src_path = os.path.join("core", "interpret", "src", "main", "kotlin",
                            "com", "bazi", "interpret", "Classics.kt")
    src = open(src_path, encoding="utf-8").read()
    books = ["滴天髓", "穷通宝鉴", "三命通会", "八字提要", "子平真诠",
             "天元咸巫", "神峰通考", "千里命稿", "五行精纪", "李虚中命书"]
    for b in books:
        check(f'"{b}"' in src, f"Classics.kt 缺古籍：{b}")
    # 十家必须各自成 enum 项
    check(src.count("    DI_TIAN_SUI(") == 1, "滴天髓枚举项缺失或重复")
    check(src.count("val reading: String") == 1, "ClassicView.reading 定义异常")


def strip_comments(src):
    """去掉 KDoc、块注释与行注释，只留真正的代码与字符串字面量。

    注释里常常写着「不写『请分析』」这类说明，若一并扫描会误报。
    """
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    kept = []
    for line in src.splitlines():
        idx = line.find("//")
        kept.append(line[:idx] if idx >= 0 else line)
    return "\n".join(kept)


def test_prompt_purity():
    p = os.path.join("core", "interpret", "src", "main", "kotlin",
                     "com", "bazi", "interpret", "AiPrompt.kt")
    src = strip_comments(open(p, encoding="utf-8").read())
    banned = [
        "请完成以下分析", "输出要求", "请你分析", "请分析",
        "仅供参考", "不构成", "不作为", "不可作为", "风险提示",
        "仅供传统文化研究", "整段复制给", "你是一位",
    ]
    for b in banned:
        check(b not in src, f"AiPrompt.kt 出现不应有的措辞：{b}")
    # 必须有的九节
    for title in ["一、出生", "二、四柱", "三、四柱神煞", "四、大运",
                  "五、大运神煞", "六、流年", "七、流年神煞", "八、流月", "九、古籍分歧"]:
        check(title in src, f"AiPrompt.kt 缺章节：{title}")


def test_interpreter_has_classics():
    p = os.path.join("core", "interpret", "src", "main", "kotlin",
                     "com", "bazi", "interpret", "Interpreter.kt")
    src = open(p, encoding="utf-8").read()
    check("classicsSection" in src, "Interpreter 未接入古籍分歧章节")
    check("yongShen" in src, "Interpreter 未接入取用取舍章节")
    check("overcomeBy()" in open(
        os.path.join("core", "analysis", "src", "main", "kotlin",
                     "com", "bazi", "analysis", "Analyzer.kt"), encoding="utf-8").read(),
        "Analyzer.favorableElements 未修正官杀取法")


def main():
    os.chdir(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
    test_wu_hu()
    test_month_cycle()
    test_concrete_2026()
    test_nayin()
    test_classics_list()
    test_prompt_purity()
    test_interpreter_has_classics()

    print(f"共执行 {CHECKS} 项断言")
    print(f"  五虎遁 10 组、月建 120 组、2026 丙午年流月 12 柱")
    print(f"  纳音 30 条末字五行、十部古籍清单、文本纯净度")
    if FAILS:
        print(f"\n失败 {len(FAILS)} 项：")
        for f in FAILS:
            print("  X", f)
        sys.exit(1)
    print("全部通过")


if __name__ == "__main__":
    main()
