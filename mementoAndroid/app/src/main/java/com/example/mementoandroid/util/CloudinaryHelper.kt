package com.example.mementoandroid.util

import android.content.Context
import android.util.Log
import com.example.mementoandroid.api.BackendConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

private const val TAG = "CloudinaryHelper"

/**
 * Upload image/audio to Cloudinary via backend signature, then direct upload.
 * Use [uploadImage] or [uploadAudio] with a file and JWT token.
 */
object CloudinaryHelper {

    private val client = OkHttpClient()

    /**
     * Upload an image and return its Cloudinary URL, or null on failure.
     */
    suspend fun uploadImage(
        context: Context,
        imageFile: File,
        authToken: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val signature = getUploadSignature(authToken, isImage = true)
                ?: return@withContext null
            val url = uploadToCloudinary(imageFile, signature, isImage = true)
            Log.d(TAG, "Image uploaded: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            null
        }
    }

    /**
     * Upload audio and return its Cloudinary URL, or null on failure.
     */
    suspend fun uploadAudio(
        context: Context,
        audioFile: File,
        authToken: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val signature = getUploadSignature(authToken, isImage = false)
                ?: return@withContext null
            val url = uploadToCloudinary(audioFile, signature, isImage = false)
            Log.d(TAG, "Audio uploaded: $url")
            url
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            null
        }
    }

    private fun getUploadSignature(authToken: String, isImage: Boolean): UploadSignature? {
        val endpoint = if (isImage) {
            "${BackendConfig.BASE_URL}/upload/signature/image"
        } else {
            "${BackendConfig.BASE_URL}/upload/signature/audio"
        }
        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $authToken")
            .get()
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Signature failed: ${response.code}")
            return null
        }
        val json = JSONObject(response.body?.string() ?: "")
        return UploadSignature(
            uploadUrl = json.getString("upload_url"),
            cloudName = json.getString("cloud_name"),
            apiKey = json.getString("api_key"),
            timestamp = json.getInt("timestamp"),
            signature = json.getString("signature"),
            folder = json.getString("folder")
        )
    }

    private fun uploadToCloudinary(
        file: File,
        signature: UploadSignature,
        isImage: Boolean
    ): String? {
        val mediaType = if (isImage) "image/*".toMediaType() else "audio/*".toMediaType()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mediaType))
            .addFormDataPart("api_key", signature.apiKey)
            .addFormDataPart("timestamp", signature.timestamp.toString())
            .addFormDataPart("signature", signature.signature)
            .addFormDataPart("folder", signature.folder)
            .apply {
                if (!isImage) addFormDataPart("resource_type", "raw")
            }
            .build()
        val request = Request.Builder()
            .url(signature.uploadUrl)
            .post(body)
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            Log.e(TAG, "Cloudinary upload failed: ${response.code}")
            return null
        }
        val json = JSONObject(response.body?.string() ?: "")
        return json.optString("secure_url", "").takeIf { it.isNotEmpty() }
    }

    private data class UploadSignature(
        val uploadUrl: String,
        val cloudName: String,
        val apiKey: String,
        val timestamp: Int,
        val signature: String,
        val folder: String
    )
}
