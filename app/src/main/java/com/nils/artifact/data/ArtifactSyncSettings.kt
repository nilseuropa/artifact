package com.nils.artifact.data

import android.content.Context

data class ArtifactSyncSettings(
    val serverUrl: String = "",
    val accessToken: String = "",
)

class ArtifactSyncSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "artifact_sync_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): ArtifactSyncSettings =
        ArtifactSyncSettings(
            serverUrl = preferences.getString(KEY_SERVER_URL, "").orEmpty(),
            accessToken = preferences.getString(KEY_ACCESS_TOKEN, "").orEmpty(),
        )

    fun save(settings: ArtifactSyncSettings) {
        preferences.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl.trim())
            .putString(KEY_ACCESS_TOKEN, settings.accessToken.trim())
            .apply()
    }

    private companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_ACCESS_TOKEN = "access_token"
    }
}
