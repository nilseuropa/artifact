package com.nils.artifact.data

import android.content.Context

data class CollectionSettings(
    val collectionName: String = "",
    val ownerName: String = "",
    val ownerEmail: String = "",
    val ownerPhone: String = "",
    val ownerNotes: String = "",
)

class CollectionSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "collection_settings",
        Context.MODE_PRIVATE,
    )

    fun load(): CollectionSettings =
        CollectionSettings(
            collectionName = preferences.getString(KEY_COLLECTION_NAME, "").orEmpty(),
            ownerName = preferences.getString(KEY_OWNER_NAME, "").orEmpty(),
            ownerEmail = preferences.getString(KEY_OWNER_EMAIL, "").orEmpty(),
            ownerPhone = preferences.getString(KEY_OWNER_PHONE, "").orEmpty(),
            ownerNotes = preferences.getString(KEY_OWNER_NOTES, "").orEmpty(),
        )

    fun save(settings: CollectionSettings) {
        preferences.edit()
            .putString(KEY_COLLECTION_NAME, settings.collectionName.trim())
            .putString(KEY_OWNER_NAME, settings.ownerName.trim())
            .putString(KEY_OWNER_EMAIL, settings.ownerEmail.trim())
            .putString(KEY_OWNER_PHONE, settings.ownerPhone.trim())
            .putString(KEY_OWNER_NOTES, settings.ownerNotes.trim())
            .apply()
    }

    private companion object {
        const val KEY_COLLECTION_NAME = "collection_name"
        const val KEY_OWNER_NAME = "owner_name"
        const val KEY_OWNER_EMAIL = "owner_email"
        const val KEY_OWNER_PHONE = "owner_phone"
        const val KEY_OWNER_NOTES = "owner_notes"
    }
}
