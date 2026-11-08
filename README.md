# 八字排盘 · Android（v0.3.0）

面向命理从业者的专业排盘工具。Kotlin + Jetpack Compose，**全程离线**（连 `INTERNET` 权限都没申请）。

输入公历或农历生辰 + 性别 + 出生地，输出四柱、十神、藏干、纳音、旬空、胎元命宫身宫、大运、流年、五行力量、旺衰、格局、调候、神煞、刑冲合害，并附规则模板解读。

本版三个重点：**日期时间改为滚轮选择**、**排盘后自动生成一段可直接交给大模型的 AI 分析指令**、**历史命例一键复用**。

---

## 一、验证边界

- **已验证** — 核心算法用可执行方式验证过：用 Python 按同一套表和同一套公式独立实现，跑通 **184 项断言**，对撞 6tail/lunar 官方示例输出、2024 年 24 节气权威时刻、子平法通行表。
- **已验证** — tyme4kt 的 API 不是猜的：逐一读取了其 master 分支源码（`SolarTime.kt` / `EightChar.kt` / `ChildLimit.kt` / `DecadeFortune.kt` / `SixtyCycle.kt` / `LunarHour.kt` / `LunarYear.kt` / `LunarMonth.kt`），按真实签名调用。
- **已验证** — 编译：GitHub Actions 上 `assembleDebug` 通过，产出可安装 APK（见第七节）。
- **待真机确认** — 滚轮手感（吸附速度、惯性）、衬线字体在各厂商 ROM 上的实际观感、深色模式对比度。这些只能在真机上看。

---

## 二、目录

```
bazi-app/
├─ settings.gradle.kts
├─ build.gradle.kts
├─ gradle/libs.versions.toml
├─ core/
│  ├─ calendar/          历法桥接：真太阳时、公农历、节气（唯一接触 tyme4kt 的层）
│  │   ├─ Model.kt             BirthInput / SectConfig / City / SolarCorrection
│  │   ├─ TrueSolarTime.kt     经度差 + 均时差（NOAA），自研
│  │   ├─ CalendarConverter.kt 公农历互转、节气、八字装配
│  │   ├─ MonthLength.kt       公历/农历每月天数（滚轮选日的依据）
│  │   └─ CityDatabase.kt      54 个城市经纬度
│  ├─ bazi/              排盘引擎（纯 Kotlin，零 Android 依赖）
│  │   ├─ GanZhi.kt            五行/天干/地支/十神
│  │   ├─ Tables.kt            藏干、纳音、十二长生、刑冲合害
│  │   ├─ Chart.kt             命盘模型（引擎与 UI 的唯一契约）
│  │   └─ BaziCalculator.kt    排盘主流程
│  ├─ analysis/          分析层
│  │   ├─ ElementScore.kt      五行力量打分 + 旺衰（每分可解释）
│  │   ├─ Pattern.kt           格局（月令取格，优先序显式化）
│  │   ├─ Tiaohou.kt           调候（10 干 × 12 月令查表）
│  │   ├─ ShenSha.kt           神煞（全表驱动）
│  │   └─ Analyzer.kt          分析总成
│  └─ interpret/         规则模板解读 + AI 指令
│      ├─ Interpreter.kt       三段式：结构化量 → 规则匹配 → 文案拼装
│      ├─ AiPrompt.kt          AI 分析指令生成（命盘全量 + 九项任务）
│      └─ TextBank.kt          文案库
├─ app/                  Android + Compose
│  ├─ MainActivity.kt          路由：只读 showResult，不用副作用监听
│  ├─ BaziViewModel.kt         录入状态 / 排盘 / 历史命例
│  ├─ data/
│  │   └─ HistoryStore.kt      历史命例存取（SharedPreferences 行格式）
│  └─ ui/
│     ├─ Theme.kt              纸/墨/朱砂 设计 Token + 衬线字号体系
│     ├─ Components.kt         ScreenHeader / Panel / Segmented / Tag 等版式件
│     ├─ WheelPicker.kt        自绘滚轮（position 为唯一状态，选中 = round）
│     ├─ InputScreen.kt        录入：年月日时分滚轮 + 历史
│     ├─ HistorySection.kt     最近排盘：点按复用 / 删除 / 清空
│     ├─ ChartScreen.kt        命盘：四柱表格 + 五行 + 格局 + 神煞
│     ├─ FortuneScreen.kt      运程：横向大运带 + 选中运的流年
│     └─ InterpretScreen.kt    断语 + AI 指令页
└─ tools/verify/         Python 校验脚本（可独立运行）
   ├─ verify_tables.py
   ├─ verify_analysis.py
   └─ verify_truesolar.py
```

**硬约束**：`core/*` 四个模块均为 `kotlin("jvm")`，禁止 import 任何 `android.*`。排盘引擎因此能在 JVM 上跑毫秒级回归，未来也能直接复用到 KMP。

---

## 三、关键算法口径

### 时间归一（最大的错误来源）

```
钟表时间（东八区）
  → 经度修正：(经度 − 120°) × 4 分钟
  → 均时差：NOAA 近似式，±16 分钟
  → 真太阳时（定日柱、时柱）
```

实测：乌鲁木齐（87.62°E）12:00 → 真太阳时 **09:37**，**午时变巳时**。不处理这一层，盘直接是错的。

夏令时**不做自动推断**——1986–1991 年区间边界易错，宁可让用户在 `SectConfig.dstOffsetMinutes` 显式填写，也不猜。

### 四柱

| 柱 | 分界 |
|----|------|
| 年柱 | **立春时刻**（不是元旦、不是正月初一） |
| 月柱 | **十二「节」**（立春/惊蛰/清明…），不是「气」 |
| 日柱 | 儒略日锚定，经 tyme4kt 换算 |
| 时柱 | 真太阳时 + 五鼠遁；23 时归属由 `ZiHourSect` 控制 |

### 基础表（全部自研，不依赖库）

十神用 **10×10 常量矩阵**查表（五行生克 × 阴阳同异），不写 25 个分支；藏干、纳音、十二长生、刑冲合害、神煞全部表驱动，改口径不动逻辑。

### 旺衰打分

天干各 1.0；地支按位置加权（**月支 3.0 司权、日支 2.0、年时支 1.0**），支内按藏干本气 1.0 / 中气 0.5 / 余气 0.2 折算。

命例实测（丙寅 癸巳 癸酉 壬子）：

| 五行 | 木 | 火 | 土 | 金 | 水 |
|------|----|----|----|----|----|
| 得分 | 1.0 | 4.5 | 0.8 | 3.5 | 4.0 |

帮身（水 4.0 + 金 3.5）占 54.35% → 偏强；月令巳火失令、日支酉金得地、天干得势；取**正财格**（月令本气丙火透干）；调候主用**辛金**。

每一条加分都能在 UI 上逐项列出，不给黑盒分数。

---

## 四、验证记录（真实运行结果）

```bash
$ python tools/verify/verify_tables.py
共执行 83 项断言
全部通过

$ python tools/verify/verify_analysis.py
共执行 21 项断言
全部通过

$ python tools/verify/verify_truesolar.py
共执行 15 项断言
  北京 116.41°E 12:00 → 真太阳时 11:32（经度-14.36分 均时差-13.61分）
  乌鲁木齐 87.62°E 12:00 → 真太阳时 09:37（经度-129.52分 均时差-13.61分）
全部通过

$ python tools/verify/verify_wheel.py
共执行 49 项断言
  旧实现：居中 1985 → 读出 1983（偏差 -2，且每次滚动都偏）
  新实现：position 为整数时第 position 行恰好居中，选中项与视觉中心一致
全部通过

$ python tools/verify/verify_history.py
共执行 16 项断言
  字段数 17、坏行跳过、同生辰去重、上限 30 条 —— 均符合 Kotlin 侧规则
全部通过
```

覆盖内容：

- **纳音**：丙寅→炉中火、癸巳→长流水、癸酉→剑锋金、壬子→桑柘木，与 6tail 官方示例逐字一致
- **十神**：甲日主对十干全表（比/劫/食/伤/才/财/杀/官/枭/印）
- **藏干**：十二支本气全表 + 权重递减
- **十二长生**：甲亥长生、甲卯帝旺、甲寅临官、乙寅帝旺、丙午帝旺、癸子临官、辛酉临官等 12 个锚点
- **空亡**：甲子旬→戌亥、甲戌旬→申酉、癸酉→戌亥
- **五行打分**：与手算期望值逐项对齐（误差 < 1e-9）
- **真太阳时**：均时差极值、每度 4 分钟、时辰边界、极端经度跨时辰

另有 `core/bazi/src/test/`、`core/calendar/src/test/`、`core/interpret/src/test/` 下的 JVM 回归测试（CI 会跑）。

---

## 五、界面

风格取「纸、墨、朱砂」三色：暖白纸底、墨色正文、朱砂点睛，衬线字做干支，不用渐变与投影。版式参照问真八字这类专业排盘工具的信息组织方式，但配色与字体是自研的。

**录入页**：年月日时分五列滚轮，改为直接滚动选择，不再手打数字。

- 滚轮用定高 `LazyColumn` + 上下对称留白让选中项落在正中，停手后按偏移量算最近项再动画吸附。**刻意没用 foundation 的 snap fling 实验 API**——那套接口在小版本间改过签名，宁可自己吸附也不赌依赖升级。
- 「日」列的天数按当前年月实时计算：公历走 `LocalDate.lengthOfMonth()`，农历走 tyme4kt 的朔望推算（大月 30 / 小月 29）。**农历月长没有规律，固定 30 天一定会让用户选到不存在的日期。**
- 农历年份自动带出「闰 X 月」选项，插在本月之后，与专业工具的习惯一致。
- 滚轮下方实时显示所属时辰与区间（如「巳时 · 09:00–10:59」），勾了真太阳时换算会额外提示。

**命盘页**：四柱做成一张真正的表——左侧细窄行标（十神/藏干/支神/星运/空亡/纳音），四列以细线分隔，天干地支用 31sp 衬线大字按五行着色，日主一格标朱砂。下方依次是五行堆叠条、格局与调候、神煞、刑冲合害、胎元命宫身宫。

**运程页**：横向大运带（可点选，当运标朱砂），选中某步运后在下方铺开它的十个流年格子，当运与当年都有标记。

**断语页**：规则模板生成的解读，按板块分卡，带序号。

---

## 六、AI 分析指令

排盘一完成，`AiPromptBuilder` 就同步生成一段 Markdown 指令（`ChartBundle.aiPrompt`），在「AI」页可一键复制，或从命盘页底部的横幅进入。

这段文本的取法有三个考量：

1. **命盘数据一次给全**。四柱、十神、藏干、藏干十神、纳音、星运、空亡、五行得分、旺衰、格局取格依据、调候、神煞、刑冲合害、八步大运、未来六年流年，全用表格给出。指令开头明确写「**不要重新排盘、不要改动任何干支**」——否则模型会自己"推算"出另一套盘。
2. **任务边界写死**。九项任务：旺衰复核、格局成败、用神喜忌、性格、事业财运、婚姻、健康、大运流年逐年、可执行建议。每条都要求落到具体干支与宫位。
3. **输出约束写死**。先结论后依据（括号注明依据）、术语附白话、分歧要标注「此处有分歧，本分析取…」、不预测生死、不诊断疾病、不承诺收益。

不联网、不调用任何 API，纯文本生成，159 行的 `AiPrompt.kt` 里没有一行网络代码。

---

## 七、v0.3.0：三个修复与历史命例

### 修复一：返回后再点排盘没反应

根因是两层叠加，两层的判据都是**对象相等性**：

1. `MutableStateFlow` 用 `equals` 去重。同一个生辰重排一次，产出的 `ChartBundle` 与上一次逐字段相等，
   `_state.update { it.copy(result = newBundle, loading = false) }` 得到的新值与旧值相等，**状态根本不发射**，
   UI 连重组都不会发生。
2. 即便发射了，`LaunchedEffect(bundle)` 的 key 也是按 `equals` 比较的——key 没变，副作用不会重启。

所以「再点一次排盘」什么都不会发生。而切换历法改变了输入、算出的是另一张盘，才「碰巧」能重排——这正是你观察到的现象。

修复：路由改成 ViewModel 里的显式 `showResult`，配合每次成功排盘自增的 `revision`；Composable 只读状态，
不再用副作用监听命盘对象。顺带的好处是旋转屏幕后仍停在结果页。

### 修复二：选了时间，排出来还是 1990

根因在滚轮取索引的算法。旧实现用定高 `LazyColumn` + `contentPadding` 做居中，
再用 `firstVisibleItemIndex + firstVisibleItemScrollOffset / itemHeight` 反推选中项。这个反推是错的：

- `firstVisibleItemIndex` 指的是**第一个可见项**，而居中项前面永远还压着 `visibleCount / 2` 项
  （它们被画进了 `contentPadding` 区域），于是读出来的索引比视觉中心小 2；
- 这个偏了 2 的索引又被 `scrollToItem` 拿去重新定位，滚一次偏一次，最终表现就是「怎么选都还是原来那个年份」。

修复：滚轮改成自绘。位置是一个连续的 `Float`（单位＝行），第 `i` 行偏移 `= (i - position) * itemHeight + centerTop`，
选中项 `= round(position)`——绘制和取值用的是**同一个量**，不存在两套口径。停手后按速度做一次短程投影再吸附。
另外在滚轮下方直接写出「将被使用的值」（如 `公历 1995-03-08 14:20 · 申时`），排盘用的是不是这一行，一眼可见。

### 新增：历史命例

- 每次排盘成功自动入栈；同一生辰不重复堆积，顶到最前；上限 30 条。
- 存 `SharedPreferences` 自定义行格式，不引 Room、不引序列化库；解析失败的行直接跳过，坏数据不连累排盘。
- 只存「输入 + 四柱/农历两个展示文本」，不存整张命盘——重算一次不到 50ms，存整盘（含 100 年流年）体积要大几十倍，
  还会在引擎口径调整后留下陈旧数据。
- 录入页顶部「最近排盘」：默认 3 条、可展开全部，点一条回填并**立即重排**，单条可删、可一键清空。

### 顶部样式

Material 的 `TopAppBar` 是 64dp 直角矩形 + 直角下沿，和卡片的 16dp 圆角不是一套语言——这就是「顶部直角」的来源。
换成自定义 `ScreenHeader`：56dp、32dp 圆形返回键、衬线标题 + 副标题；页面内切换从下划线 Tab 换成与「公历/农历」
同款的胶囊 `Segmented`；面板圆角统一 16dp、分段块 12dp、标签 5dp。

---

## 八、构建

```bash
# 本地编译需要：JDK 17+、Android Studio（含 SDK Platform 34）
# 仓库未提交 gradle-wrapper.jar（二进制无法离线生成），首次克隆后先补：
gradle wrapper --gradle-version 8.7

./gradlew :core:calendar:test :core:bazi:test :core:interpret:test   # 先跑引擎回归
./gradlew :app:assembleDebug
```

技术栈：Kotlin 2.0.21 / AGP 8.6.1 / Compose BOM 2024.09.03 / tyme4kt 1.5.2（MIT）/ minSdk 26 / targetSdk 34。

> minSdk 取 26 是因为核心引擎用 `java.time`，26 起原生支持，省掉 desugaring 依赖。若要下探到 24，需开启 `coreLibraryDesugaring`。

### 云端构建（GitHub Actions）

`.github/workflows/android.yml` 已配好，推到 GitHub 后自动执行：

1. `verify-engine` —— 跑五个 Python 校验脚本（184 项断言）。算法不对，后面直接不构建。
2. `build` —— 装 JDK 17 + SDK 34，跑 `:core:calendar:test` / `:core:bazi:test` / `:core:interpret:test`，再构建 Debug APK。
3. 产物 `bazi-debug-apk` 挂在 Actions 页面该次运行的 Artifacts 中，下载即可安装。

这样即便本机没有 Android 环境，也能拿到可安装的 APK。构建失败时 `build-reports` 会带上 Gradle 报错与测试报告。

---

## 九、已知限制与待办

**未实现（P1/P2）**

- 命例本地存储（Room）、命例对比
- 导出 PNG / PDF、分享
- 时辰未知时的三柱容错模式
- 城市库仅 54 个主要城市，未覆盖区县级；暂无手动输入经纬度
- 神煞仅 9 类（天乙/文昌/禄神/羊刃/桃花/驿马/华盖/将星/月德 + 空亡），未含天德、红鸾天喜、孤辰寡宿等
- 格局仅月令取格主路径，未做从格、专旺格、化气格

**数据待专业审校**

- **调候表**：依《穷通宝鉴》通行本录入 10 干 × 12 月令，主用较确定，**辅助用神各版本取舍有出入**，建议从业者核对后再上线。
- **神煞查法**：不同典籍口径不一（如天乙贵人有「甲戊庚牛羊」与「甲戊兼牛羊」两说），当前取通行本，已做成纯数据表，改口径只需改表。
- **藏干余气**：权重 1.0/0.5/0.2 为教学折中值，非某一流派原典数值。

**待真机确认**

- 滚轮吸附手感与惯性（真机上才好判断）
- 衬线字体（`FontFamily.Serif`）在各厂商 ROM 上是否落到宋体，否则会退化成黑体
- 深色模式下五行色的对比度

tyme4kt 在 Android 目标上的依赖解析已随 CI 构建通过：核心模块是 `kotlin("jvm")`，Android 侧直接消费其 JVM 变体即可。

---

## 十、下一步建议

1. 装到真机上看滚轮手感，必要时调 `itemHeight` 与吸附阈值
2. 接入 Room 做命例管理——从业者要反复查客户盘，这是留存关键
3. 用权威排盘工具批量生成 200+ 命例 fixture，补上 `core:bazi` 的回归测试
4. 请一位从业者审校调候表与神煞查法，把「数据待审校」的标签去掉
5. AI 指令可再扩：支持自定义"追问模板"（只看婚姻 / 只看某年）
