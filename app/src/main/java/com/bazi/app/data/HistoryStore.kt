package com.bazi.app.data

import android.content.Context
import com.bazi.calendar.CalendarType
import com.bazi.calendar.City
import com.bazi.calendar.Gender
import com.bazi.calendar.ZiHourSect

/**
 * 一条历史命例。
 *
 * 只存「输入 + 两个展示用文本」，不存整张命盘：
 * 命盘重算一次不到 50ms，而序列化整盘（含 100 年流年）会让存储膨胀几十倍，
 * 还会在引擎口径调整后留下陈旧数据。
 */
data class HistoryEntry(
    val id: Long,
    val calendarType: CalendarType,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val leapMonth: Boolean,
    val gender: Gender,
    val city: City,
    val useTrueSolarTime: Boolean,
    val ziHour: ZiHourSect,
    /** 排盘时的四柱，仅用于列表展示。 */
    val ganZhi: String,
    /** 排盘时的农历文本，仅用于列表展示。 */
    val lunarText: String
) {
    val dateText: String
        get() = "%04d-%02d-%02d %02d:%02d".format(year, month, day, hour, minute)

    val calendarText: String
        get() = if (calendarType == CalendarType.SOLAR) "公历" else "农历"

    val sexText: String
        get() = if (gender == Gender.MALE) "乾造" else "坤造"

    /** 输入是否等价（不看 id），用于避免同一生辰反复入栈。 */
    fun sameInput(other: HistoryEntry): Boolean =
        calendarType == other.calendarType &&
            year == other.year && month == other.month && day == other.day &&
            hour == other.hour && minute == other.minute &&
            leapMonth == other.leapMonth && gender == other.gender &&
            city.name == other.city.name &&
            useTrueSolarTime == other.useTrueSolarTime && ziHour == other.ziHour
}

/**
 * 历史命例存储。
 *
 * 用 SharedPreferences + 自定义行格式，不引入 Room / 序列化库：
 * 数据结构是一条条定长字段的记录，行内 `|` 分隔即可，读写都在毫秒级，
 * 也不给构建增加注解处理器依赖。
 */
class HistoryStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun load(): List<HistoryEntry> {
        val raw = prefs.getString(KEY_RECORDS, null) ?: return emptyList()
        return raw.lineSequence()
            .mapNotNull { decode(it) }
            .take(MAX_RECORDS)
            .toList()
    }

    /** 写入一条，同一生辰的旧记录会被顶到最前而不是重复堆积。 */
    fun add(entry: HistoryEntry): List<HistoryEntry> {
        val next = (listOf(entry) + load().filterNot { it.sameInput(entry) })
            .take(MAX_RECORDS)
        persist(next)
        return next
    }

    fun remove(id: Long): List<HistoryEntry> {
        val next = load().filterNot { it.id == id }
        persist(next)
        return next
    }

    fun clear() {
        prefs.edit().remove(KEY_RECORDS).apply()
    }

    private fun persist(entries: List<HistoryEntry>) {
        prefs.edit()
            .putString(KEY_RECORDS, entries.joinToString("\n") { encode(it) })
            .apply()
    }

    // ------------------------------------------------------------ 编解码

    private fun encode(e: HistoryEntry): String = listOf(
        e.id.toString(),
        e.calendarType.name,
        e.year.toString(),
        e.month.toString(),
        e.day.toString(),
        e.hour.toString(),
        e.minute.toString(),
        if (e.leapMonth) "1" else "0",
        e.gender.name,
        e.city.name,
        e.city.province,
        e.city.longitude.toString(),
        e.city.latitude.toString(),
        if (e.useTrueSolarTime) "1" else "0",
        e.ziHour.name,
        e.ganZhi,
        e.lunarText
    ).joinToString(SEP)

    /** 解析失败一律返回 null 并跳过该行——历史记录坏了不能连累排盘。 */
    private fun decode(line: String): HistoryEntry? {
        if (line.isBlank()) return null
        val f = line.split(SEP)
        if (f.size != FIELD_COUNT) return null
        return runCatching {
            HistoryEntry(
                id = f[0].toLong(),
                calendarType = CalendarType.valueOf(f[1]),
                year = f[2].toInt(),
                month = f[3].toInt(),
                day = f[4].toInt(),
                hour = f[5].toInt(),
                minute = f[6].toInt(),
                leapMonth = f[7] == "1",
                gender = Gender.valueOf(f[8]),
                city = City(
                    name = f[9],
                    province = f[10],
                    longitude = f[11].toDouble(),
                    latitude = f[12].toDouble()
                ),
                useTrueSolarTime = f[13] == "1",
                ziHour = ZiHourSect.valueOf(f[14]),
                ganZhi = f[15],
                lunarText = f[16]
            )
        }.getOrNull()
    }

    private companion object {
        const val PREF_NAME = "bazi_history"
        const val KEY_RECORDS = "records_v1"
        const val SEP = "|"
        const val FIELD_COUNT = 17
        const val MAX_RECORDS = 30
    }
}
