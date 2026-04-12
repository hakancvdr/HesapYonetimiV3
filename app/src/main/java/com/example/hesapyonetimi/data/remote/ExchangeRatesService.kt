package com.example.hesapyonetimi.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ücretsiz JSON API (anahtar yok). Başarısız olursa null döner.
 * @see https://github.com/fawazahmed0/currency-api
 */
@Singleton
class ExchangeRatesService @Inject constructor() {

    @Volatile
    private var cachedBanner: String? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    suspend fun tryCrossRatesBanner(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedBanner != null && now - cachedAtMillis < 300_000L) {
            return@withContext cachedBanner
        }
        val fresh = fetchBannerFresh()
        if (fresh != null) {
            cachedBanner = fresh
            cachedAtMillis = now
        }
        return@withContext fresh
    }

    private fun fetchBannerFresh(): String? {
        return runCatching {
            val url = URL("https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/try.min.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
            }
            conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
        }.mapCatching { body ->
            val root = JSONObject(body)
            val tryRates = root.optJSONObject("try") ?: root
            val usdPerTry = tryRates.optDouble("usd", 0.0)
            val eurPerTry = tryRates.optDouble("eur", 0.0)
            if (usdPerTry <= 0.0 || eurPerTry <= 0.0) return@mapCatching null
            val tryPerUsd = 1.0 / usdPerTry
            val tryPerEur = 1.0 / eurPerTry
            String.format(
                Locale("tr", "TR"),
                "USD/TRY ≈ %,.2f  ·  EUR/TRY ≈ %,.2f",
                tryPerUsd,
                tryPerEur
            )
        }.getOrNull()
    }
}
