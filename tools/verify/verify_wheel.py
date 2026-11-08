"""
滚轮取值公式校验。

Kotlin 侧（app/ui/WheelPicker.kt）与这里用的是同一组公式，此处把它抽出来单独跑，
用来回答两个问题：

1. 旧实现为什么会「怎么选还是原来那个值」——把 LazyColumn 的语义（含 contentPadding）
   还原出来，看 `firstVisibleItemIndex + offset/itemHeight` 与视觉中心差多少；
2. 新实现的「选中项 = round(position)」是否在所有边界上都与绘制位置一致。

注意：这里验证的是公式，不是 Kotlin 代码本身。公式是排盘输入的唯一来源，
它错了整张盘就错了，所以值得单独钉住。
"""

import math


def kround(x):
    """Kotlin 的 Float.roundToInt()：.5 向正无穷取整。Python 的 round() 是银行家舍入，不能直接用。"""
    return math.floor(x + 0.5)


ITEM = 38.0          # 每行高度 dp
VISIBLE = 5          # 可见行数
CENTER_TOP = ITEM * ((VISIBLE - 1) // 2)   # 中间行距顶部的距离 = 76dp

checks = 0
fails = []


def check(ok, label):
    global checks
    checks += 1
    if not ok:
        fails.append(label)


# ---------------------------------------------------------------- 旧实现（LazyColumn）

def old_settled_index(first_visible_index, first_visible_offset, item=ITEM):
    """旧代码：firstVisibleItemIndex + round(offset / itemHeight)。"""
    return first_visible_index + kround(first_visible_offset / item)


# 旧实现的自证：把滚轮摆在「视觉中心是 1985」的位置，看旧公式报什么
# 视觉中心 = 85 → 视口顶部对应内容坐标 = 85*ITEM - CENTER_TOP
def old_reading_for_center(center_index, item=ITEM, visible=VISIBLE):
    center_top = item * ((visible - 1) // 2)
    scroll = center_index * item - center_top          # 视口顶部的内容坐标
    first_visible = int(scroll // item)                # 第一个可见项
    offset = scroll - first_visible * item             # 该项已滚过的像素
    return first_visible, offset, old_settled_index(first_visible, offset, item)


fv, off, reported = old_reading_for_center(85)
check(reported == 85 - 2,
      f"旧实现应复现「居中 1985 却读成 {85 - 2}」的偏差，实际读到 {reported}")
check(fv == 85 - 2 and off == 0,
      f"旧实现的 firstVisibleItemIndex={fv} offset={off}，应为中心项上方第 2 行")

# 偏差随滚动累积：连续滚 5 次，每次读到的值都比视觉中心小 2
for step in range(5):
    center = 90 - step
    _, _, r = old_reading_for_center(center)
    check(r == center - 2, f"旧实现第 {step} 次滚动：居中 {center} 读成 {r}")

# ---------------------------------------------------------------- 新实现（自绘）

def new_row_y(index, position, item=ITEM, visible=VISIBLE):
    """Kotlin: dy = (index - position) * itemPx + centerTop。"""
    center_top = item * ((visible - 1) // 2)
    return (index - position) * item + center_top


def new_selected(position):
    """Kotlin: 选中项 = position.roundToInt()。"""
    return kround(position)


# 1) 位置为整数 k 时，第 k 行必须正好落在中心带上
for k in (0, 1, 45, 90, 200):
    check(new_row_y(k, float(k)) == CENTER_TOP, f"position={k} 时第 {k} 行未居中")
    check(new_selected(float(k)) == k, f"position={k} 的选中项应为 {k}")

# 2) 上下一行分别高出/低于中心整整一行
check(new_row_y(91, 90.0) == CENTER_TOP + ITEM, "第 91 行应比中心低一行")
check(new_row_y(89, 90.0) == CENTER_TOP - ITEM, "第 89 行应比中心高一行")

# 3) 拖动换算：手指上滑 2 行（delta = -2*ITEM）→ 位置 +2
def drag(position, delta, item=ITEM, last=200):
    return min(max(position - delta / item, 0.0), float(last))


p = drag(90.0, -2 * ITEM)
check(p == 92.0, f"上滑 2 行后位置应为 92，实际 {p}")
check(new_selected(p) == 92, f"上滑 2 行后选中项应为 92，实际 {new_selected(p)}")
p = drag(90.0, 2 * ITEM)
check(new_selected(p) == 88, "下滑 2 行后选中项应为 88")

# 4) 边界夹取：不会越过 0 或 lastIndex
check(drag(1.0, 100 * ITEM) == 0.0, "向下拖越界应夹在 0")
check(drag(199.0, -100 * ITEM) == 200.0, "向上拖越界应夹在 lastIndex")

# 5) 任意小数位置，选中项与「离中心最近的行」一致
for frac in (0.0, 0.2, 0.49, 0.5, 0.51, 0.8, 0.99):
    for base in (0, 90, 199):
        pos = base + frac
        candidates = [i for i in (base - 1, base, base + 1, base + 2) if 0 <= i <= 200]
        dists = {i: abs(new_row_y(i, pos) - CENTER_TOP) for i in candidates}
        best = min(dists.values())
        check(dists[new_selected(pos)] <= best + 1e-9,
              f"position={pos} 选中 {new_selected(pos)}，但它不是离中心最近的行")

# 6) 速度投影后吸附：方向必须与甩动方向一致，且落在整行
def settle(position, velocity, item=ITEM, last=200):
    projected = position - velocity / item * 0.12
    return min(max(kround(projected), 0), last)


check(settle(90.0, -ITEM * 60) > 90, "向上快甩应落在更大的序号")
check(settle(90.0, ITEM * 60) < 90, "向下快甩应落在更小的序号")
check(settle(90.0, 0.0) == 90, "无速度应原地吸附")
check(all(isinstance(settle(x, v), int) for x in (0.0, 90.4, 200.0) for v in (-900.0, 0.0, 900.0)),
      "吸附结果必须是整行")

# ---------------------------------------------------------------- 输出

print(f"共执行 {checks} 项断言")
if fails:
    print(f"失败 {len(fails)} 项：")
    for f in fails:
        print("  x", f)
    raise SystemExit(1)

print("  旧实现：居中 1985 → 读出 1983（偏差 -2，且每次滚动都偏）")
print("  新实现：position 为整数时第 position 行恰好居中，选中项与视觉中心一致")
print("全部通过")
