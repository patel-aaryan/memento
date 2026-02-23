package com.example.mementoandroid.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

/**
 * Thrown when a backend request fails. [statusCode] is the HTTP status (e.g. 401 for unauthorized).
 */
class BackendException(message: String, val statusCode: Int? = null) : Exception(message)

/**
 * Lightweight HTTP helpers for calling the Memento backend.
 * All functions are suspend and run on Dispatchers.IO; use from a coroutine.
 *
 * Optional [token] is sent as "Authorization: Bearer &lt;token&gt;" for protected endpoints.
 */
private const val TAG = "BackendClient"

object BackendClient {

    /**
     * GET [path]. Path should start with "/" (e.g. "/health").
     * Returns Result with JSONObject body on 2xx, or Result.failure with message from body "detail" or HTTP status.
     */
    suspend fun get(path: String, token: String? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            request("GET", path, body = null, token = token)
        }

    /**
     * POST [path] with optional JSON [body]. Path should start with "/".
     * [errorMessageFallback] is used when the error response has no "detail" (e.g. empty body); receives status code.
     */
    suspend fun post(
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        errorMessageFallback: ((Int) -> String)? = null
    ): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            request("POST", path, body = body, token = token, errorMessageFallback = errorMessageFallback)
        }

    /**
     * PUT [path] with optional JSON [body].
     */
    suspend fun put(path: String, body: JSONObject? = null, token: String? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            request("PUT", path, body = body, token = token)
        }

    /**
     * PATCH [path] with optional JSON [body].
     */
    suspend fun patch(path: String, body: JSONObject? = null, token: String? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            request("PATCH", path, body = body, token = token)
        }

    /**
     * DELETE [path]. Returns success with empty JSONObject on 2xx (e.g. 204).
     */
    suspend fun delete(path: String, token: String? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            request("DELETE", path, body = null, token = token)
        }

    /**
     * GET /health - useful to verify backend is reachable.
     */
    suspend fun healthCheck(): Result<JSONObject> = get("/health")

    /**
     * GET [path] and parse response as JSON array (e.g. /albums, /images/album/1).
     */
    suspend fun getArray(path: String, token: String? = null): Result<JSONArray> =
        withContext(Dispatchers.IO) {
            requestArray("GET", path, token = token)
        }

    private fun request(
        method: String,
        path: String,
        body: JSONObject?,
        token: String?,
        errorMessageFallback: ((Int) -> String)? = null
    ): Result<JSONObject> {
        val pathNormalized = if (path.startsWith("/")) path else "/$path"
        val url = URL(BackendConfig.BASE_URL + pathNormalized)
        return try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                useCaches = false
                requestMethod = method
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                if (token != null) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
            }
            val bodyStr = body?.toString() ?: ""
            Log.d(TAG, "HTTP request: method=$method url=${url} Accept=application/json " +
                "Authorization=${if (token != null) "Bearer ***" else "null"} " +
                "Content-Type=${if (body != null) "application/json" else "null"} body=$bodyStr")
            if (body != null) {
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (responseCode in 200..299) {
                val json = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
                Result.success(json)
            } else {
                val fallback = errorMessageFallback?.invoke(responseCode) ?: "HTTP $responseCode"
                val message = try {
                    if (responseBody.isNotBlank()) {
                        val json = JSONObject(responseBody)
                        json.optString("detail", fallback)
                    } else fallback
                } catch (_: Exception) {
                    fallback
                }
                Result.failure(BackendException(message, responseCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun requestArray(method: String, path: String, token: String?): Result<JSONArray> {
        val pathNormalized = if (path.startsWith("/")) path else "/$path"
        val url = URL(BackendConfig.BASE_URL + pathNormalized)
        Log.d(TAG, "HTTP request: method=$method url=$url Accept=application/json Authorization=${if (token != null) "Bearer ***" else "null"} body=null")
        return try {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                useCaches = false
                requestMethod = method
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-cache, no-store")
                setRequestProperty("Pragma", "no-cache")
                if (token != null) setRequestProperty("Authorization", "Bearer $token")
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (responseCode in 200..299) {
                if (pathNormalized.contains("/images/album/")) {
                    Log.d(TAG, "HTTP response: GET $pathNormalized body=$responseBody")
                }
                val arr = if (responseBody.isBlank()) JSONArray() else JSONArray(responseBody)
                Result.success(arr)
            } else {
                val message = try {
                    if (responseBody.isNotBlank()) JSONObject(responseBody).optString("detail", "HTTP $responseCode")
                    else "HTTP $responseCode"
                } catch (_: Exception) { "HTTP $responseCode" }
                Result.failure(BackendException(message, responseCode))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
