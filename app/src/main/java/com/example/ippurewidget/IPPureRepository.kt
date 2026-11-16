package com.example.ippurewidget

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class MetricPoint(val score: Int, val proxyPingMs: Int)

data class IPPureInfo(
    val ip: String,
    val country: String,
    val countryCode: String,
    val city: String,
    val asn: String,
    val org: String,
    val fraudScore: Int,
    val proxyPingMs: Int,
    val directPingMs: Int,
    val isResidential: Boolean,
    val isBroadcast: Boolean,
    val updatedAt: String,
    val isRefreshing: Boolean = false
)

object IPPureRepository {
    private const val PREF_NAME = "ippure_flclash_v2_cache"
    private const val KEY_JSON = "last_json"
    private const val KEY_PROXY_PING = "last_proxy_ping"
    private const val KEY_DIRECT_PING = "last_direct_ping"
    private const val KEY_TIME = "last_time"
    private const val KEY_HISTORY = "dual_history_v2"

    private val client = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
        .connectTimeout(3500, TimeUnit.MILLISECONDS)
        .readTimeout(3500, TimeUnit.MILLISECONDS)
        .build()

    fun getCachedWithHistory(context: Context, markRefreshing: Boolean = false): Pair<IPPureInfo, List<MetricPoint>>? {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_JSON, null) ?: return null
        val proxyPing = prefs.getInt(KEY_PROXY_PING, -1)
        val directPing = prefs.getInt(KEY_DIRECT_PING, -1)
        val cachedTime = prefs.getString(KEY_TIME, "--:--") ?: "--:--"
        val history = parseHistory(prefs.getString(KEY_HISTORY, "[]"))
        return parseJson(cachedJson, proxyPing, directPing, cachedTime, markRefreshing) to history
    }

    suspend fun fetchWithHistory(context: Context): Pair<IPPureInfo, List<MetricPoint>>? =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val infoDeferred = async { fetchRawIpJson() }
            val proxyPingDeferred = async { measureLatencyWarm("https://cp.cloudflare.com/generate_204") }
            val directPingDeferred = async { measureLatencyWarm("https://connect.rom.miui.com/generate_204") }

            val rawJson = infoDeferred.await()
            val proxyPing = proxyPingDeferred.await()
            val directPing = directPingDeferred.await()

            if (!rawJson.isNullOrEmpty()) {
                val info = parseJson(rawJson, proxyPing, directPing, timeStr, false)
                val updatedHistory = appendHistory(prefs.getString(KEY_HISTORY, "[]"), MetricPoint(info.fraudScore, info.proxyPingMs))
                prefs.edit()
                    .putString(KEY_JSON, rawJson)
                    .putInt(KEY_PROXY_PING, proxyPing)
                    .putInt(KEY_DIRECT_PING, directPing)
                    .putString(KEY_TIME, timeStr)
                    .putString(KEY_HISTORY, serializeHistory(updatedHistory))
                    .apply()
                return@withContext info to updatedHistory
            }
            return@withContext getCachedWithHistory(context, markRefreshing = false)
        }

    private fun fetchRawIpJson(): String? = try {
        val req = Request.Builder().url("https://my.ippure.com/v1/info").header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
    } catch (_: Exception) { null }

    private fun measureLatencyWarm(url: String): Int = try {
        val req = Request.Builder().url(url).head().build()
        client.newCall(req).execute().close()
        val start = SystemClock.elapsedRealtime()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 204) (SystemClock.elapsedRealtime() - start).toInt().coerceAtLeast(1) else -1
        }
    } catch (_: Exception) { -1 }

    private fun appendHistory(rawArray: String?, point: MetricPoint): List<MetricPoint> {
        val list = parseHistory(rawArray).toMutableList()
        list.add(point)
        while (list.size > 10) list.removeAt(0)
        return list
    }

    private fun serializeHistory(list: List<MetricPoint>): String {
        val arr = JSONArray()
        list.forEach { pt -> arr.put(JSONObject().apply { put("s", pt.score); put("p", pt.proxyPingMs) }) }
        return arr.toString()
    }

    private fun parseHistory(rawArray: String?): List<MetricPoint> {
        val arr = JSONArray(rawArray ?: "[]")
        return List(arr.length()) { i ->
            val obj = arr.optJSONObject(i)
            MetricPoint(obj?.optInt("s", 0) ?: 0, obj?.optInt("p", 0) ?: 0)
        }
    }

    private fun parseJson(raw: String, proxyPing: Int, directPing: Int, timeStr: String, isRefreshing: Boolean): IPPureInfo {
        val obj = JSONObject(raw)
        val rawOrg = obj.optString("asOrganization", obj.optString("organization", obj.optString("isp", "")))
        return IPPureInfo(
            ip = obj.optString("ip", "未知 IP"),
            country = obj.optString("country", ""),
            countryCode = obj.optString("countryCode", ""),
            city = obj.optString("city", ""),
            asn = obj.optString("asn", ""),
            org = rawOrg,
            fraudScore = obj.optInt("fraudScore", 0),
            proxyPingMs = proxyPing,
            directPingMs = directPing,
            isResidential = obj.optBoolean("isResidential", false),
            isBroadcast = obj.optBoolean("isBroadcast", false),
            updatedAt = timeStr,
            isRefreshing = isRefreshing
        )
    }

    fun countryCodeToFlag(code: String): String {
        if (code.length != 2) return "🌐"
        val upper = code.uppercase(Locale.US)
        val first = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
        val second = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }
}
