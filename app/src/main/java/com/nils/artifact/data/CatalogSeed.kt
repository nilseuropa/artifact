package com.nils.artifact.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CatalogData(
    val manufacturers: List<CatalogManufacturer>,
) {
    companion object {
        val Empty = CatalogData(emptyList())
    }
}

data class CatalogManufacturer(
    val id: String,
    val name: String,
    val models: List<CatalogModel>,
)

data class CatalogModel(
    val id: String,
    val name: String,
    val productionYearStart: Int?,
    val productionYearEnd: Int?,
)

object CatalogSeed {
    fun load(context: Context): CatalogData {
        val json = context.assets.open("catalog_seed.json").bufferedReader().use { reader ->
            JSONObject(reader.readText())
        }
        val manufacturers = json.getJSONArray("manufacturers").mapObjects { manufacturer ->
            CatalogManufacturer(
                id = manufacturer.getString("id"),
                name = manufacturer.getString("name"),
                models = manufacturer.getJSONArray("models").mapObjects { model ->
                    CatalogModel(
                        id = model.getString("id"),
                        name = model.getString("name"),
                        productionYearStart = model.optionalInt("productionYearStart"),
                        productionYearEnd = model.optionalInt("productionYearEnd"),
                    )
                },
            )
        }
        return CatalogData(manufacturers = manufacturers)
    }
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }

private fun JSONObject.optionalInt(key: String): Int? =
    if (isNull(key)) null else optInt(key)
