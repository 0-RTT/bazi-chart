package com.bazi.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import com.bazi.calendar.City
import com.bazi.calendar.CityDatabase
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * 取本机定位，只为了出生地的**经度**。
 *
 * 为什么只要经度：真太阳时 = 钟表时 + 经度差 + 均时差，其中经度差是主项
 * （每度 4 分钟），纬度完全不参与。所以**粗定位就够**，不必申请精确定位，
 * 也不必让系统弹「精确位置」那种更吓人的授权。
 *
 * 为什么不申请 INTERNET：定位由系统服务完成，本应用只接收结果；
 * 拿到坐标后不做逆地理编码（那要联网），而是去 [CityDatabase.nearest]
 * 里找最近的地级市当地名，再把**真实经度**写进出生地。
 *
 * `getCurrentLocation` 是 API 30 起才有的；26–29 上没有等价的单次请求接口，
 * 只能退到「最后已知位置」——这类设备上取不到时会明确告知，让用户手选城市。
 */
object GeoLocator {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    /** 单次定位的上限。超时就退回最后已知位置，不让界面干等。 */
    private const val TIMEOUT_MS = 6_000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    /** 取一次位置。返回 null 表示没权限、定位关闭，或超时没拿到。 */
    suspend fun locate(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = providersOf(manager)
        if (providers.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // 必须给超时：个别机型 getCurrentLocation 的回调既不返回位置也不返回 null，
            // 不设上限，界面就永远停在「定位中…」。
            val fresh = withTimeoutOrNull(TIMEOUT_MS) {
                var found: Location? = null
                for (provider in providers) {
                    found = currentLocation(manager, provider)
                    if (found != null) break
                }
                found
            }
            if (fresh != null) return fresh
        }
        return lastKnown(manager, providers)
    }

    /**
     * 定位结果 → 出生地。
     *
     * 城市名取最近的地级市（给人看），经纬度取**设备实测值**（给真太阳时算）。
     * 二者有意分开：城市中心点与用户实际位置可能差几十公里，
     * 前者只负责「这是哪儿」，后者才参与换算。
     */
    fun toCity(location: Location): City = toCity(location.longitude, location.latitude)

    fun toCity(longitude: Double, latitude: Double): City {
        val nearest = CityDatabase.nearest(longitude, latitude)
        return City(
            name = nearest.name,
            province = nearest.province,
            longitude = longitude,
            latitude = latitude
        )
    }

    private fun providersOf(manager: LocationManager): List<String> =
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun currentLocation(manager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val signal = CancellationSignal()
            val executor = Executor { it.run() }
            runCatching {
                manager.getCurrentLocation(provider, signal, executor) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { runCatching { signal.cancel() } }
        }

    private fun lastKnown(manager: LocationManager, providers: List<String>): Location? =
        providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
}
