package com.example.musicbrainzrenamer

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface MusicBrainzApi {
    @GET("recording")
    suspend fun searchRecording(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 10
    ): RecordingSearchResponse

    companion object {
        fun create(): MusicBrainzApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "MusicBrainzRenamer/1.0 (youremail@example.com)")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://musicbrainz.org/ws/2/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MusicBrainzApi::class.java)
        }
    }
}
