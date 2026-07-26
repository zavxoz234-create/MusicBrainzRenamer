package com.example.musicbrainzrenamer

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface DeezerApi {
    @GET("search/album")
    suspend fun searchAlbum(
        @Query("q") query: String,
        @Query("limit") limit: Int = 5
    ): DeezerAlbumSearch

    companion object {
        fun create(): DeezerApi {
            return Retrofit.Builder()
                .baseUrl("https://api.deezer.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DeezerApi::class.java)
        }
    }
}
