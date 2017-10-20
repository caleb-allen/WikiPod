package io.caleballen.wikipod.data

import com.google.gson.annotations.SerializedName

/**
 * Created by caleb on 10/20/2017.
 */

data class WikiGeoSearch(
        @SerializedName("batchcomplete")
        val batchComplete: String?,
        val query: Query?
)

data class Query(
        @SerializedName("geosearch")
        val geoSearch: List<GeoSearchResult>?
)

data class GeoSearchResult(
        @SerializedName("pageid")
        val pageId: Int?,
        val ns: Int?,
        val title: String?,
        val lat: Float?,
        val lon: Float?,
        val dist: Float?
)