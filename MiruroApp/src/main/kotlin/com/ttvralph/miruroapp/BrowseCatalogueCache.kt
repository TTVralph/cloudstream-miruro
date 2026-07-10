package com.ttvralph.miruroapp

import android.content.Context
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ttvralph.miruroapp.data.AnimeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BrowseCatalogueCache(
    context: Context,
    format: String
) {
    private val preferences = context.getSharedPreferences(
        "anistream_browse_${format.lowercase()}",
        Context.MODE_PRIVATE
    )
    private val mapper = jacksonObjectMapper()

    fun read(): List<AnimeItem> = runCatching {
        val json = preferences.getString(KEY_ITEMS, null) ?: return emptyList()
        mapper.readValue(json, object : TypeReference<List<AnimeItem>>() {})
    }.getOrElse {
        preferences.edit().remove(KEY_ITEMS).apply()
        emptyList()
    }

    suspend fun write(items: List<AnimeItem>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext
        runCatching { mapper.writeValueAsString(items) }
            .onSuccess { json -> preferences.edit().putString(KEY_ITEMS, json).apply() }
    }

    private companion object {
        const val KEY_ITEMS = "items_v1"
    }
}
