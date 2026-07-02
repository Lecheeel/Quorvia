package com.quorvia.app.feature.explore

import com.quorvia.app.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class QrngClient(
    private val baseUrl: String = BuildConfig.QRNG_PROXY_BASE_URL,
) {
    fun fetchUInt16(length: Int): List<Int> {
        require(length in 1..1024)
        check(baseUrl.isNotBlank()) { "QRNG proxy URL is not configured." }

        val endpoint = URL("${baseUrl.trimEnd('/')}/v1/qrng?type=uint16&length=$length")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")

        return try {
            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (statusCode !in 200..299) {
                error("QRNG proxy returned HTTP $statusCode.")
            }

            val json = JSONObject(body)
            val values = json.getJSONArray("values")
            List(values.length()) { index -> values.getInt(index) }
        } finally {
            connection.disconnect()
        }
    }
}

