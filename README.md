# 八字排盘 · Android（v0.1.0）

面向命理从业者的专业排盘工具。Kotlin + Jetpack Compose，**全程离线**（连 `INTERNET` 权限都没申请）。

输入公历或农历生辰 + 性别 + 出生地，输出四柱、十神、藏干、纳音、旬空、胎元命宫身宫、大运、流年、五行力量、旺衰、格局、调候、神煞、刑冲合害，并附规则模板解读。

---

## 一、先说明验证边界（重要）

本机环境**没有 JDK、没有 Android SDK、Shell 也无法访问 Maven 仓库**，所以这份代码：

- **已验证** — 核心算法用可执行方式验证过：用 Python 按同一套表和同一套公式独立实现，跑通 **119 项断言**，对撞 6tail/lunar 官方示例输出、2024 年 24 节气权威时刻、子平法通行表。
- **已验证** — tyme4kt 的 API 不是猜的：逐一读取了其 master 分支源码（`SolarTime.kt` / `EightChar.kt` / `ChildLimit.kt` / `DecadeFortune.kt` / `SixtyCycle.kt` / `LunarHour.kt` / `LunarYear.kt`），按真实签名调用。
- **待确认** — Android 端未经编译：Gradle 配置、Compose 代码、tyme4kt 的 Android 依赖解析，都需要你首次在 Android Studio 里构建时确认。

这是有意为之的取舍：与其交一堆"看起来能编译"却没验证过算法的代码，不如把可验证的部分真正验证掉，并明确标出不可验证的边界。

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
│  └─ interpret/         规则模板解读
│      ├─ Interpreter.kt       三段式：结构化量 → 规则匹配 → 文案拼装
│      └─ TextBank.kt          文案库
├─ app/                  Android + Compose
│  ├─ MainActivity.kt
│  ├─ BaziViewModel.kt
│  └─ ui/  Theme / InputScreen / ChartScreen / FortuneScreen
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
```

覆盖内容：

- **纳音**：丙寅→炉中火、癸巳→长流水、癸酉→剑锋金、壬子→桑柘木，与 6tail 官方示例逐字一致
- **十神**：甲日主对十干全表（比/劫/食/伤/才/财/杀/官/枭/印）
- **藏干**：十二支本气全表 + 权重递减
- **十二长生**：甲亥长生、甲卯帝旺、甲寅临官、乙寅帝旺、丙午帝旺、癸子临官、辛酉临官等 12 个锚点
- **空亡**：甲子旬→戌亥、甲戌旬→申酉、癸酉→戌亥
- **五行打分**：与手算期望值逐项对齐（误差 < 1e-9）
- **真太阳时**：均时差极值、每度 4 分钟、时辰边界、极端经度跨时辰

另有 `core/bazi/src/test/` 下的 JVM 回归测试（需 Gradle 环境运行）。

---

## 五、构建

```bash
# 本地编译需要：JDK 17+、Android Studio（含 SDK Platform 34）
# 仓库未提交 gradle-wrapper.jar（二进制无法离线生成），首次克隆后先补：
gradle wrapper --gradle-version 8.7

./gradlew :core:bazi:test      # 先跑引擎回归
./gradlew :app:assembleDebug
```

技术栈：Kotlin 2.0.21 / AGP 8.6.1 / Compose BOM 2024.09.03 / tyme4kt 1.5.2（MIT）/ minSdk 26 / targetSdk 34。

> minSdk 取 26 是因为核心引擎用 `java.time`，26 起原生支持，省掉 desugaring 依赖。若要下探到 24，需开启 `coreLibraryDesugaring`。

### 云端构建（GitHub Actions）

`.github/workflows/android.yml` 已配好，推到 GitHub 后自动执行：

1. `verify-engine` —— 跑三个 Python 校验脚本（119 项断言）。算法不对，后面直接不构建。
2. `build` —— 装 JDK 17 + SDK 34，跑 `:core:bazi:test`，再构建 Debug APK。
3. 产物 `bazi-debug-apk` 挂在 Actions 页面该次运行的 Artifacts 中，下载即可安装。

这样即便本机没有 Android 环境，也能拿到可安装的 APK。构建失败时 `build-reports` 会带上 Gradle 报错与测试报告。

---

## 六、已知限制与待办

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

**未验证**

- Android 编译与真机运行
- tyme4kt 在 Android 目标上的依赖解析（KMP 库，可能需要 `-jvm` 变体；核心模块已是 `kotlin("jvm")`，理论上可直接被 Android 消费）

---

## 七、下一步建议

1. 在 Android Studio 里跑通 `assembleDebug`，把编译问题清零
2. 接入 Room 做命例管理——从业者要反复查客户盘，这是留存关键
3. 用权威排盘工具批量生成 200+ 命例 fixture，补上 `core:bazi` 的回归测试
4. 请一位从业者审校调候表与神煞查法，把「数据待审校」的标签去掉
