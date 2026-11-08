"""
历史命例存储格式校验。

HistoryStore 用「17 字段 + `|` 分隔 + 换行分条」的自定义格式存 SharedPreferences。
这种手写格式有两个典型事故：

1. 字段数与解码时不一致 → 所有记录静默失效（而且不报错，用户只会觉得"历史没了"）；
2. 某条记录损坏 → 解码抛异常，连累整个列表读不出来。

这里按 Kotlin 侧同一套规则做往返验证，把这两类问题钉死在 CI 上。
"""

checks = 0
fails = []

SEP = "|"
FIELD_COUNT = 17
MAX_RECORDS = 30


def check(ok, label):
    global checks
    checks += 1
    if not ok:
        fails.append(label)


# ---------------------------------------------------------------- 参照实现

def encode(e):
    return SEP.join([
        str(e["id"]),
        e["calendarType"],
        str(e["year"]), str(e["month"]), str(e["day"]),
        str(e["hour"]), str(e["minute"]),
        "1" if e["leapMonth"] else "0",
        e["gender"],
        e["city"]["name"], e["city"]["province"],
        repr(e["city"]["lon"]), repr(e["city"]["lat"]),
        "1" if e["useSolar"] else "0",
        e["ziHour"],
        e["ganZhi"],
        e["lunarText"],
    ])


def decode(line):
    if not line.strip():
        return None
    f = line.split(SEP)
    if len(f) != FIELD_COUNT:
        return None
    try:
        return {
            "id": int(f[0]),
            "calendarType": f[1],
            "year": int(f[2]), "month": int(f[3]), "day": int(f[4]),
            "hour": int(f[5]), "minute": int(f[6]),
            "leapMonth": f[7] == "1",
            "gender": f[8],
            "city": {
                "name": f[9], "province": f[10],
                "lon": float(f[11]), "lat": float(f[12]),
            },
            "useSolar": f[13] == "1",
            "ziHour": f[14],
            "ganZhi": f[15],
            "lunarText": f[16],
        }
    except ValueError:
        return None


def same_input(a, b):
    return (
        a["calendarType"] == b["calendarType"]
        and a["year"] == b["year"] and a["month"] == b["month"] and a["day"] == b["day"]
        and a["hour"] == b["hour"] and a["minute"] == b["minute"]
        and a["leapMonth"] == b["leapMonth"] and a["gender"] == b["gender"]
        and a["city"]["name"] == b["city"]["name"]
        and a["useSolar"] == b["useSolar"] and a["ziHour"] == b["ziHour"]
    )


def add(records, entry):
    return ([entry] + [r for r in records if not same_input(r, entry)])[:MAX_RECORDS]


def sample(**over):
    e = {
        "id": 1762531200000,
        "calendarType": "SOLAR",
        "year": 1986, "month": 5, "day": 29, "hour": 0, "minute": 30,
        "leapMonth": False,
        "gender": "MALE",
        "city": {"name": "北京", "province": "北京", "lon": 116.41, "lat": 39.9},
        "useSolar": True,
        "ziHour": "LATE",
        "ganZhi": "丙寅 癸巳 癸酉 壬子",
        "lunarText": "丙寅年 四月廿一",
    }
    e.update(over)
    return e


# ---------------------------------------------------------------- 往返

base = sample()
round_trip = decode(encode(base))
check(round_trip == base, f"基本往返失败：{round_trip}")

# 农历 + 闰月 + 特殊城市名
lunar = sample(
    calendarType="LUNAR", year=2020, month=4, day=23, leapMonth=True,
    city={"name": "乌鲁木齐", "province": "新疆", "lon": 87.62, "lat": 43.79},
    ganZhi="庚子 辛巳 癸亥 甲寅", lunarText="庚子年 闰四月廿三",
)
check(decode(encode(lunar)) == lunar, "农历闰月往返失败")

# 城市名里带空格与括号也要能原样回来
odd = sample(city={"name": "阿勒泰 地区", "province": "新疆(北)", "lon": 88.14, "lat": 47.85})
check(decode(encode(odd))["city"]["name"] == "阿勒泰 地区", "特殊城市名往返失败")

# 字段数：编码结果必须正好 17 段
check(len(encode(base).split(SEP)) == FIELD_COUNT, "编码字段数不等于 17")

# ---------------------------------------------------------------- 容错

check(decode("") is None, "空行应返回 None")
check(decode("a|b|c") is None, "字段数不足应返回 None")
check(decode(encode(base) + "|extra") is None, "字段数过多应返回 None")
check(decode("x" + encode(base)[1:]) is None, "id 非法应返回 None")
check(decode(encode(base).replace("1986", "19x6", 1)) is None, "年份非法应返回 None")

# 一条坏记录不能连累整表
blob = "\n".join([encode(sample(id=1)), "broken|line", encode(sample(id=2, hour=13))])
parsed = [r for r in (decode(x) for x in blob.split("\n")) if r]
check(len(parsed) == 2, f"坏行应被跳过并保留 2 条，实际 {len(parsed)}")

# ---------------------------------------------------------------- 去重与上限

records = []
records = add(records, sample(id=1))
records = add(records, sample(id=2))                     # 同生辰 → 不新增
check(len(records) == 1, f"同生辰应去重，实际 {len(records)} 条")
check(records[0]["id"] == 2, "去重时应保留最新一条并顶到最前")

records = add(records, sample(id=3, hour=13))            # 换时辰 → 新增
check(len(records) == 2, "不同时辰应新增一条")

for i in range(40):
    records = add(records, sample(id=100 + i, day=(i % 28) + 1, hour=(i % 23)))
check(len(records) <= MAX_RECORDS, f"超过上限未截断：{len(records)}")

# 往返整表（模拟一次写入再读回）
blob = "\n".join(encode(r) for r in records)
reloaded = [decode(x) for x in blob.split("\n")]
check(all(r is not None for r in reloaded), "整表往返存在解析失败项")
check(len(reloaded) == len(records), "整表往返条数不一致")

# ---------------------------------------------------------------- 输出

print(f"共执行 {checks} 项断言")
if fails:
    print(f"失败 {len(fails)} 项：")
    for f in fails:
        print("  x", f)
    raise SystemExit(1)
print("  字段数 17、坏行跳过、同生辰去重、上限 30 条 —— 均符合 Kotlin 侧规则")
print("全部通过")
