package com.nils.artifact.data

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object ArtifactSyncClient {
    fun uploadBackup(settings: ArtifactSyncSettings, backupFile: File): String {
        require(backupFile.isFile) {
            "Backup file is missing."
        }

        val connection = openConnection(settings, "/api/backup").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/zip")
            setFixedLengthStreamingMode(backupFile.length())
        }

        return try {
            connection.outputStream.use { output ->
                backupFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            val body = connection.readBody()
            connection.requireSuccess(body)
            body.jsonMessageOrNull() ?: "Uploaded backup to server."
        } finally {
            connection.disconnect()
        }
    }

    fun downloadBackup(settings: ArtifactSyncSettings, targetFile: File): String {
        val connection = openConnection(settings, "/api/backup").apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/zip")
        }

        return try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val body = connection.readBody()
                error("Server download failed: HTTP $code${body.detailSuffix()}")
            }

            connection.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            "Downloaded backup from server."
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        settings: ArtifactSyncSettings,
        path: String,
    ): HttpURLConnection {
        val baseUrl = normalizedBaseUrl(settings.serverUrl)
        return (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 120_000
            useCaches = false
            settings.accessToken.trim().takeIf { token -> token.isNotEmpty() }?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
        }
    }

    private fun normalizedBaseUrl(serverUrl: String): String {
        val value = serverUrl.trim().trimEnd('/')
        require(value.startsWith("http://") || value.startsWith("https://")) {
            "Server URL must start with http:// or https://."
        }
        return value
    }

    private fun HttpURLConnection.readBody(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { reader -> reader.readText() }
            .orEmpty()
            .trim()
    }

    private fun HttpURLConnection.requireSuccess(body: String) {
        val code = responseCode
        if (code !in 200..299) {
            error("Server upload failed: HTTP $code${body.detailSuffix()}")
        }
    }

    private fun String.detailSuffix(): String =
        takeIf { value -> value.isNotBlank() }?.let { value -> ": $value" }.orEmpty()

    private fun String.jsonMessageOrNull(): String? =
        runCatching {
            JSONObject(this).optString("message").takeIf { message -> message.isNotBlank() }
        }.getOrNull()
}
