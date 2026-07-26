package com.example.musicbrainzrenamer

import com.google.gson.annotations.SerializedName

data class RecordingSearchResponse(
    @SerializedName("recordings") val recordings: List<Recording>
)

data class Recording(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("length") val length: Long?,
    @SerializedName("artist-credit") val artistCredit: List<ArtistCredit>?,
    @SerializedName("releases") val releases: List<Release>?
) {
    fun getArtistName(): String = artistCredit?.joinToString(", ") { it.name } ?: "Unknown Artist"
    fun getAlbumName(): String = releases?.firstOrNull()?.title ?: "Unknown Album"
}

data class ArtistCredit(
    @SerializedName("name") val name: String,
    @SerializedName("artist") val artist: Artist
)

data class Artist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class Release(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("date") val date: String?
)
