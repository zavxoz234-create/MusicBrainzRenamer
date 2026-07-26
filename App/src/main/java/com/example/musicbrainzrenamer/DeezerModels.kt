package com.example.musicbrainzrenamer

import com.google.gson.annotations.SerializedName

data class DeezerAlbumSearch(
    @SerializedName("data") val data: List<DeezerAlbum>
)

data class DeezerAlbum(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String,
    @SerializedName("cover_big") val coverBig: String,
    @SerializedName("cover_xl") val coverXl: String,
    @SerializedName("artist") val artist: DeezerArtist
)

data class DeezerArtist(
    @SerializedName("name") val name: String
)
