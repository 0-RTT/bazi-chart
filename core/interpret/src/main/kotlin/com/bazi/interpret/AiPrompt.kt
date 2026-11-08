package com.bazi.interpret

import com.bazi.calendar.Gender
import com.bazi.engine.AnnualFortune
import com.bazi.engine.BaziCalculator
import com.bazi.engine.BaziChart
import com.bazi.engine.LuckPillar
import com.bazi.engine.MonthlyFortune
import com.bazi.engine.Pillar
import com.bazi.engine.DiZhi
import com.bazi.engine.PillarPosition
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

/**
 * 结构因子。
 *
 * 只装**可核对的事实**，不装判断：日主强弱、格局、用神一概不在这里。
 * 字段边界划在「能被重算」这一侧：「日主在月令为墓」可以查表核对，
 * 「日主偏弱」不能；「帮身占 47.3%」可以重算，「身弱用印」不能。
 */
data class StructureFactors(
    /** 月令。 */
    val monthCommand: DiZhi,
    /** 月令本气与透干情况，如「本气戊土，天干未透；藏戊辛丁」。 */
    val monthDetail: String,
    /** 日主在月令的十二长生，如「墓」。 */
    val dayMasterPhase: String,
    /** 月令是否生扶日主。 */
    val commandSupported: Boolean,
    /** 日支是否生扶日主。 */
    val groundSupported: Boolean,
    /** 天干中帮身（比劫、印）的位数。 */
    val supportStems: Int,
    /** 帮身力量占比，0..1。 */
    val supportRatio: Double,
    /** 五行力量占比，0..1，按从高到低。 */
    val elementShares: List<Pair<String, Double>>,
    /** 四柱未见的五行。 */
    val missing: List<String>,
    /** 天干关系，如「甲己五合（年干—月干，化土）」。 */
    val ganRelations: List<String>,
    /** 地支关系，如「卯戌火六合」「午午自刑」。 */
    val zhiRelations: List<String>
)

/**
 * 盘面文本生成器。
 *
 * 全文三段：**任务**（要模型做什么）、**盘面数据**（排盘、大运、流年、流月）、
 * **结构因子**（可复核但不含判断的事实）。
 *
 * 为什么要有任务段：这份文本的唯一用途就是粘给大模型。早先只给数据不给指令，
 * 是想让使用者自己决定怎么问；实际用下来，每次都要重打一遍维度与输出要求，
 * 十次有八次会漏掉「关键事件要给时间范围」。把要求写死在生成端，盘与问就不会走散。
 *
 * 结构因子是「不给结论」与「不给支撑」之间的那条线：旺衰、格局、调候、喜用
 * 一概不写，先给了模型只会顺着抄；但完全不给支撑，它又会凭印象乱判强弱。
 * 于是只给**能被重算**的东西（得令得地、力量占比、刑冲合害清单、月令气候），
 * 把「身弱」「正官格」「用神为某字」留给它自己推——判错时，读者对着因子
 * 一算就知道错在哪一步。
 *
 * 因子由 app 层从分析层取出后传入：本模块只排版、不计算，也不依赖
 * `core:analysis`（与神煞入参同一理由）。任务段分两段输出，第一段只给
 * 结论与最显著的三个维度，其余留到用户要求展开时再写。按用户决定，
 * 全文不加免责声明。
 *
 * 纯 Kotlin，无 Android 依赖，可在 JVM 上直接断言文本内容。
 */
object AiPromptBuilder {

    /** 流年的展示窗口：参考年前 5 年至后 15 年。 */
    private const val ANNUAL_BACK = 5
    private const val ANNUAL_FORWARD = 15

    /**
     * @param shenSha 各宫位的神煞名。core:interpret 刻意不依赖 core:analysis，
     *               故由调用方（app 层）传字符串进来，本层只负责排版。
     * @param factors 结构因子。同上，由 app 层从分析层取出后传进来——
     *                依赖边界不变，本层只排版、不计算。
     */
    fun build(
        chart: BaziChart,
        referenceYear: Int = LocalDate.now().year,
        shenSha: Map<PillarPosition, List<String>> = emptyMap(),
        factors: StructureFactors? = null
    ): String = buildString {
        appendTask()
        appendBasic(chart)
        appendPillars(chart, shenSha)
        if (factors != null) appendFactors(factors)
        appendLuck(chart, referenceYear)
        appendAnnuals(chart, referenceYear)
        appendMonthlies(chart, referenceYear)
    }

    // ------------------------------------------------------------ 〇、任务

    /**
     * 任务段。
     *
     * 这一版有两处结构性改动，都是被「读了实际输出才知道不对」逼出来的：
     *
     * 1. **给判读次序，而不只是维度清单**。上一版把五个流派并列，模型于是在七个
     *    维度里各写一段，样样都提、样样不能核对。现在先给主链——月令取格 → 论成败救应
     *    → 取用神 → 看刑冲合害 → 落宫位 → 岁运引动——再要求每条结论都挂回某一环。
     *    次序取自《渊海子平》「先看提纲为重」与《子平真诠》「用神专求月令」。
     *
     * 2. **分两段输出**。十项一次铺开，读完率远低于信息密度：先给总论、定盘、
     *    三个最显著维度与十年内的时间轴，末尾问要不要展开。
     *
     * 其余几条是被真实输出逼出来的纪律：标置信度、重结论多路闭环、
     * 技术有利不等于必然发生、术语要落地、不许复述盘面。
     */
    private fun StringBuilder.appendTask() {
        appendLine("# 八字命盘分析")
        appendLine()
        appendLine("你是一位精通中国传统命理的分析师：子平格局、旺衰扶抑、调候用神、盲派取象、")
        appendLine("神煞参断都有涉猎，能说清各派分歧在哪里、自己又为什么取这一派。")
        appendLine("下面是一份完整的八字盘面，请据此出分析。")
        appendLine()

        appendLine("## 一、先校盘")
        appendLine()
        appendLine("先按你自己的算法核对四柱、藏干、副星、纳音、旬空、大运起运。")
        appendLine("凡与下表不一致的，先指出差异和可能原因（换日流派、真太阳时、节气交接），")
        appendLine("然后**按下表继续分析**，不要擅自改盘。")
        appendLine()

        appendLine("## 二、判读主链：按这个次序推，不要跳步")
        appendLine()
        appendLine("《渊海子平》：「先看提纲为重，次用年日时支，合成格局，方可断之。」")
        appendLine("《子平真诠》：「八字用神，专求月令。」下面六步是这套次序的现代切分，")
        appendLine("后面每一节的结论都要能挂回其中一环。")
        appendLine()
        appendLine("1. **月令取格**：月支本气是否透干？透则以透干为格，不透以本气为虚格；")
        appendLine("   再看月令有无合化改向。要写到「格神是哪一个字、从哪一柱透出」。")
        appendLine("2. **论成败救应**：格神有没有被伤（伤官见官、枭神夺食、财坏印、比劫争财）；")
        appendLine("   受伤时四柱里有没有救应（印制伤、食化劫、合去忌神）。")
        appendLine("   写成「格—伤—救」三句话，不要丢一个格局名就往下走。")
        appendLine("3. **取用神**：扶抑、调候、通关、病药四法中**取一为主、一至二为辅**，")
        appendLine("   并写明为何取此舍彼。用神取错后面全错，这一步必须给出取舍理由。")
        appendLine("4. **看刑冲合害**：哪些字被冲、被合、被刑；旬空的字是否压在关键位上；")
        appendLine("   救应在哪一柱。")
        appendLine("5. **落宫位**：年主祖上早年、月主父母兄弟与环境、日支主配偶、时主子女晚年。")
        appendLine("   论父母以星法为主（父看偏财、母看正印），宫位只作早年环境之辅。")
        appendLine("6. **岁运引动**：大运改的是格局层次，不是重写原局；流年定应期。")
        appendLine("   写「某运使格局由此转彼」，不写「某运好／不好」。")
        appendLine()
        appendLine("六步走完再动笔。正文不必复述推演过程，但每条结论都要能指回其中一步。")
        appendLine()

        appendLine("## 三、判读纪律：硬约束")
        appendLine()
        appendLine("- **分清事实与推测**：盘面数据与结构因子是事实，你的推断要标明是推断。")
        appendLine("  不编造具体数额、具体疾病名、具体人名与单位。")
        appendLine("- **每条判断标置信度**：高／中／低。旺衰临界、格局两可、时辰存疑一律标低，")
        appendLine("  并写明分歧点在哪。")
        appendLine("- **重结论必须多路闭环**：涉及无子、重病、官非、重大破财、婚变、伤残这类结论，")
        appendLine("  必须同时给出宫位、十神、大运流年三条独立证据；三者不齐只能写「信号」，")
        appendLine("  不许下定论。")
        appendLine("- **技术有利不等于必然发生**：大运流年只改变原局条件的表现，不改写原局。")
        appendLine("  该说「此运利于某事」，不写「此运必发生某事」。")
        appendLine("- **不用现实结果反推**：已发生的事可以拿来验证判断，不能当作命理证据回填。")
        appendLine("- **术语要落地**：枭神夺食、伤官见官、羊刃驾杀这类词，第一次出现时后跟一句白话。")
        appendLine("- **神煞只作旁证**：单见不论吉凶；两见以上，或与刑冲合害同指一处时才提。")
        appendLine("- **不要复述盘面**：四柱、藏干、纳音、空亡、大运流年这些表已给出，")
        appendLine("  正文直接给判断，不要把表格翻译成句子再念一遍。")
        appendLine()

        appendLine("## 四、分析维度：必答八项")
        appendLine()
        appendLine("每项都要落到「哪一柱、哪个十神、哪组刑冲合害」，不许只写性格形容词。")
        appendLine()
        appendLine("0. **性格**（先写这一项，其余七项都从它推出来）：性情底色、遇事的反应模式、")
        appendLine("   长处与短板、压力下的变形")
        appendLine("1. **事业**：行业与岗位性质、体制内还是市场、升迁节奏、能否创业、转折在哪步运")
        appendLine("2. **财运**：财从何处来（正财／偏财／食伤生财／官护财）、聚散节奏、破财节点、守财方式")
        appendLine("3. **婚恋**：配偶形象与相识途径、婚恋时间窗口、相处模式、风险点")
        appendLine("4. **子女**：缘分厚薄、生育时间窗口、与子女亲疏")
        appendLine("5. **六亲**：父母、兄弟姐妹、祖辈各对应哪一宫，助力与牵累各是什么")
        appendLine("6. **健康**：先天薄弱的脏腑、易发问题、需要留意的年份")
        appendLine("7. **学业**：读书与考试运、适合的学习方式、专业方向")
        appendLine()
        appendLine("**选答**：按本盘显著度挑 2–3 项写，不显著就直说不显著，不要凑数——")
        appendLine("出身家境、人际与合伙、居所迁移、官非与意外。")
        appendLine()
        appendLine("八项必答与选答都属于第九节的**第二段**；第一段只写其中信息量最大的三项，")
        appendLine("不要把八项缩写成八句敷衍话凑齐。")
        appendLine()

        appendLine("## 五、关键事件：给成时间轴表")
        appendLine()
        appendLine("凡断定会发生的事，四项缺一不可：")
        appendLine()
        appendLine("- **时间**：精确到年，必要时到月；必须落在下表的大运与流年上，")
        appendLine("  不许写「二十几岁」「中年」这类无法核对的说法")
        appendLine("- **吉凶**：吉／凶／半吉半凶；半吉半凶要写明吉在何处、凶在何处")
        appendLine("- **程度**：决定性／重大／中等／轻微")
        appendLine("- **影响**：对现实生活意味着什么（换工作、破财、成婚、伤病……）")
        appendLine()
        appendLine("用表格：年份 ｜ 事件 ｜ 吉凶 ｜ 程度 ｜ 依据。一行一件事，按年份排序。")
        appendLine()

        appendLine("## 六、建议要能执行")
        appendLine()
        appendLine("针对这个盘的具体结构给，不要通用鸡汤。按三档写：")
        appendLine("**现在就能做**（三个月内）、**三年内**、**长期**。")
        appendLine("这一节属于第二段，第一段只给结论与时间轴，不必写建议。")
        appendLine()

        appendLine("## 七、流月只在落到月份时用")
        appendLine()
        appendLine("数据里的流月只有一年。只在需要把应期精确到月时引用，")
        appendLine("按「交节日 → 月建干支 → 与原局的作用」三样写，不要逐月罗列吉凶。")
        appendLine()

        appendLine("## 八、不确定就说不确定")
        appendLine()
        appendLine("时辰存疑、出生地不明、流派分歧都算不确定性。")
        appendLine("先说明它会让哪些结论失效，再给条件式结论（「若为辰时则……，若为卯时则……」）。")
        appendLine()

        appendLine("## 九、输出分两段：先给结论，再问要不要展开")
        appendLine()
        appendLine("**第一段**（一次给完，不超过 1200 字）：")
        appendLine()
        appendLine("1. 总论，不超过三句")
        appendLine("2. 定盘：格、用神、日主强弱，各一句，各带置信度")
        appendLine("3. 三个最显著的维度：从八项里挑你判断信息量最大的三项")
        appendLine("4. 时间轴表：只列**未来十年**内的关键事件")
        appendLine("5. 最后一句：其余维度与更远年份要不要展开")
        appendLine()
        appendLine("**第二段**（我在上一段末尾要求展开时才写）：其余维度 + 完整时间轴 + 建议三档。")
        appendLine()
        appendLine("不要在没被要求时把十项一次铺开——读完率比覆盖面重要。")
        appendLine()

        appendLine("---")
        appendLine()
        appendLine("以下为盘面数据。")
        appendLine()
    }

    // ------------------------------------------------------------ 一、结构因子

    /**
     * 结构因子表。
     *
     * 位置刻意放在四柱表之后：先看盘、再看因子，模型才不会把因子当主证据。
     * 表里每一行都能被复核，模型可以在「结论要能核对」里逐条回指——
     * 这也是把「强弱／格局／用神」留给它自己判的底气：
     * 它若判错，读者拿因子一算就知道错在哪一步。
     */
    private fun StringBuilder.appendFactors(f: StructureFactors) {
        appendLine("## 结构因子")
        appendLine()
        appendLine("| 因子 | 结果 |")
        appendLine("| --- | --- |")
        appendLine("| 月令 | ${f.monthCommand.cn} · ${f.monthDetail} |")
        appendLine("| 日主在月令 | ${f.dayMasterPhase} |")
        appendLine("| 得令 | ${yesNo(f.commandSupported)}，月令是否生扶日主 |")
        appendLine("| 得地 | ${yesNo(f.groundSupported)}，日支是否生扶日主 |")
        // 两个口径不能挤在同一行：位数只数四天干，占比却是含地支藏干的全盘加权，
        // 写在一起会被读成「这 N 位贡献了 X%」。
        appendLine("| 天干帮身 | ${f.supportStems} 位（比劫与印，只数四天干） |")
        appendLine(
            "| 帮身力量占比 | %.1f%%（含地支藏干的全盘加权） |"
                .format(Locale.ROOT, f.supportRatio * 100)
        )
        appendLine("| 五行力量 | ${shareText(f.elementShares)} |")
        appendLine("| 四柱未见 | ${if (f.missing.isEmpty()) "无" else f.missing.joinToString("、")} |")
        appendLine("| 天干关系 | ${orNone(f.ganRelations)} |")
        appendLine("| 地支关系 | ${orNone(f.zhiRelations)} |")
        appendLine("| 月令气候 | ${seasonNote(f.monthCommand)} |")
        appendLine()
        appendLine(
            "权重口径：天干各 1.0；地支月 3.0、日 2.0、年时各 1.0；" +
                "支内按本气／中气／余气折算。天干关系只判有没有合与合化何物，不判化与不化。"
        )
        appendLine()
    }

    private fun yesNo(v: Boolean): String = if (v) "是" else "否"

    private fun orNone(items: List<String>): String =
        if (items.isEmpty()) "无" else items.joinToString("；")

    private fun shareText(shares: List<Pair<String, Double>>): String =
        shares.joinToString(" ｜ ") {
            it.first + " " + "%.1f".format(Locale.ROOT, it.second * 100) + "%"
        }

    /** 月令气候。只写季节与燥湿，不写「该用什么」，那是读盘人的事。 */
    private fun seasonNote(zhi: DiZhi): String = when (zhi) {
        DiZhi.YIN -> "孟春，木当令，寒气未尽而阳气初升"
        DiZhi.MAO -> "仲春，木当令，气候温润"
        DiZhi.CHEN -> "季春，土湿，木有余气，雨水渐多"
        DiZhi.SI -> "孟夏，火当令，燥热渐起"
        DiZhi.WU -> "仲夏，火当令，最燥热"
        DiZhi.WEI -> "季夏，土燥，火有余气，湿热交蒸"
        DiZhi.SHEN -> "孟秋，金当令，暑退而燥"
        DiZhi.YOU -> "仲秋，金当令，最燥"
        DiZhi.XU -> "季秋，土燥，金有余气，天凉而干"
        DiZhi.HAI -> "孟冬，水当令，寒湿初起"
        DiZhi.ZI -> "仲冬，水当令，最寒"
        DiZhi.CHOU -> "季冬，土寒，水有余气，湿冷"
    }


    // ------------------------------------------------------------ 二、排盘

    private fun StringBuilder.appendBasic(chart: BaziChart) {
        val moment = chart.moment
        val input = chart.input
        val sex = if (input.gender == Gender.MALE) "男（乾造）" else "女（坤造）"

        appendLine("## 排盘")
        appendLine()
        appendLine("- 性别：$sex")
        appendLine("- 公历（钟表时）：${dateTime(moment.clockTime)}")
        appendLine(
            "- 真太阳时：${dateTime(moment.trueSolarTime)}" +
                if (moment.solarTimeApplied) "" else "（未启用，按钟表时定时辰）"
        )
        appendLine(
            "- 时差校正：经度 %+.1f 分（出生地经度 %.2f°E，东经 120° 基准）、均时差 %+.1f 分%s".format(
                Locale.ROOT,
                moment.correction.longitudeMinutes,
                input.city.longitude,
                moment.correction.equationOfTimeMinutes,
                if (moment.correction.dstMinutes != 0) "、夏令时 −%d 分".format(Locale.ROOT, moment.correction.dstMinutes) else ""
            )
        )
        appendLine("- 农历：${moment.lunar.text}，属${moment.lunar.zodiac}")
        appendLine("- 出生地：${input.city.displayName}（北纬 %.2f°）".format(Locale.ROOT, input.city.latitude))
        appendLine(
            "- 节气：当令 ${moment.currentTerm.name}（${dateTime(moment.currentTerm.time)}）；" +
                "下一节 ${moment.nextJie.name}（${dateTime(moment.nextJie.time)}）"
        )
        appendLine(
            "- 起运：${chart.startLuck.text}，${dateTime(chart.startLuck.startTime)} 交运，起运虚岁 ${chart.startLuck.startAge}"
        )
        appendLine(
            "- 胎元 ${chart.fetalOrigin}　胎息 ${chart.fetalBreath}　命宫 ${chart.ownSign}　身宫 ${chart.bodySign}"
        )
        appendLine()
    }

    private fun StringBuilder.appendPillars(
        chart: BaziChart,
        shenSha: Map<PillarPosition, List<String>>
    ) {
        val p = chart.pillars

        appendLine("四柱：**${chart.ganZhiText}**（年　月　日　时）")
        appendLine()
        appendLine("日主：**${chart.dayMaster.cn}${chart.dayMaster.wuXing.cn}**（${chart.dayMaster.polarity}干）")
        appendLine()
        appendLine("| 项目 | 年柱 | 月柱 | 日柱 | 时柱 |")
        appendLine("| --- | --- | --- | --- | --- |")
        appendLine("| 主星 | " + p.joinToString(" | ") { it.ganTenGod?.cn ?: "日主" } + " |")
        appendLine("| 天干 | " + p.joinToString(" | ") { "${it.gan.cn}${it.gan.wuXing.cn}" } + " |")
        appendLine("| 地支 | " + p.joinToString(" | ") { "${it.zhi.cn}${it.zhi.wuXing.cn}" } + " |")
        appendLine("| 藏干 | " + p.joinToString(" | ") { hiddenStems(it) } + " |")
        appendLine("| 副星 | " + p.joinToString(" | ") { hiddenGods(it) } + " |")
        appendLine("| 纳音 | " + p.joinToString(" | ") { it.naYin } + " |")
        appendLine("| 星运 | " + p.joinToString(" | ") { it.growthPhase.cn } + " |")
        appendLine("| 自坐 | " + p.joinToString(" | ") { it.ownGrowthPhase.cn } + " |")
        // 每柱各按自身干支所属之旬取空亡，四柱通常各不相同；
        // 另用「落空」标出哪几柱的地支正落日柱旬空，两者语义不同，不可合并。
        appendLine("| 空亡 | " + p.joinToString(" | ") { pillar -> pillar.voidBranches.joinToString("") { it.cn } } + " |")
        appendLine("| 落空 | " + p.joinToString(" | ") { if (it.isEmptyBranch) "空" else "—" } + " |")
        if (shenSha.isNotEmpty()) {
            // 空亡已单独成行，神煞栏不再重复列它
            appendLine(
                "| 神煞 | " + p.joinToString(" | ") { pillar ->
                    shenSha[pillar.position].orEmpty()
                        .filter { it != "空亡" }
                        .joinToString("、")
                        .ifEmpty { "—" }
                } + " |"
            )
        }
        appendLine()
        appendLine("旬空（依日柱）：${chart.voidBranches.joinToString("、") { it.cn }}")
        appendLine()
        if (shenSha.isNotEmpty()) {
            appendLine("神煞按通行查法：天乙、国印、金舆、福星年干日干并查；亡神、劫煞年支日支并查；")
            appendLine("华盖、将星排除本宫自指；学堂、词馆取年柱纳音的长生与临官。")
            appendLine("各家口径有出入，若你的查法不同，以你的为准并注明。")
            appendLine()
        }
    }

    private fun hiddenStems(pillar: Pillar): String =
        pillar.hiddenStems.joinToString(" ") { it.hidden.gan.cn }

    private fun hiddenGods(pillar: Pillar): String =
        pillar.hiddenStems.joinToString("/") { it.tenGod.cn }

    // ------------------------------------------------------------ 三、大运

    private fun StringBuilder.appendLuck(chart: BaziChart, referenceYear: Int) {
        appendLine("## 大运")
        appendLine()
        appendLine("| 大运 | 虚岁 | 公历年 | 干十神 | 支十神 | 星运 | 纳音 | 空亡 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- |")
        chart.luckPillars.forEach { luck -> appendLuckRow(luck, referenceYear) }
        appendLine()
    }

    private fun StringBuilder.appendLuckRow(luck: LuckPillar, referenceYear: Int) {
        val mark = if (referenceYear in luck.startYear..luck.endYear) " ←当前" else ""
        appendLine(
            "| ${luck.ganZhi}$mark | ${luck.startAge}–${luck.endAge} | ${luck.startYear}–${luck.endYear} " +
                "| ${luck.ganTenGod.cn} | ${luck.zhiMainTenGod.cn} | ${luck.growthPhase.cn} " +
                "| ${luck.naYin} | ${if (luck.isEmptyBranch) "空" else "—"} |"
        )
    }

    // ------------------------------------------------------------ 四、流年

    private fun StringBuilder.appendAnnuals(chart: BaziChart, referenceYear: Int) {
        appendLine("## 流年")
        appendLine()
        appendLine("| 年份 | 虚岁 | 干支 | 十神 | 纳音 | 空亡 | 所属大运 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        annualWindow(chart, referenceYear).forEach { annual -> appendAnnualRow(annual, referenceYear) }
        appendLine()
    }

    private fun StringBuilder.appendAnnualRow(annual: AnnualFortune, referenceYear: Int) {
        val mark = if (annual.year == referenceYear) " ←今年" else ""
        appendLine(
            "| ${annual.year}$mark | ${annual.nominalAge} | ${annual.ganZhi} | ${annual.ganTenGod.cn} " +
                "| ${annual.naYin} | ${if (annual.isEmptyBranch) "空" else "—"} | ${annual.luckGanZhi} |"
        )
    }

    // ------------------------------------------------------------ 五、流月

    private fun StringBuilder.appendMonthlies(chart: BaziChart, referenceYear: Int) {
        val months = BaziCalculator.monthliesOf(chart, referenceYear)
        appendLine("## 流月（$referenceYear 年）")
        appendLine()
        if (months.isEmpty()) {
            appendLine("该年流月数据缺失。")
            appendLine()
            return
        }
        appendLine("| 月建 | 干支 | 十神 | 纳音 | 空亡 | 交节 | 终节 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- |")
        months.forEach { m -> appendMonthRow(m) }
        appendLine()
        appendLine("月建以节为界，寅月自立春起算，丑月交于次年立春。")
        appendLine()
    }

    private fun StringBuilder.appendMonthRow(m: MonthlyFortune) {
        val mark = if (m.isCurrent) " ←当月" else ""
        appendLine(
            "| ${m.monthName}${m.zhi.cn}月$mark | ${m.ganZhi} | ${m.ganTenGod.cn} " +
                "| ${m.naYin} | ${if (m.isEmptyBranch) "空" else "—"} " +
                "| ${dateTime(m.startTime)} | ${dateTime(m.endTime)} |"
        )
    }

    // ------------------------------------------------------------ 工具

    private fun annualWindow(chart: BaziChart, referenceYear: Int): List<AnnualFortune> =
        chart.annuals.filter { it.year in (referenceYear - ANNUAL_BACK)..(referenceYear + ANNUAL_FORWARD) }

    private fun dateTime(t: LocalDateTime): String =
        "%04d-%02d-%02d %02d:%02d".format(
            Locale.ROOT, t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute
        )
}
