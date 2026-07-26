package com.example.musicbrainzrenamer

import android.content.Context
import android.net.Uri
import com.mpatric.mp3agic.ID3v2
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import java.io.File

object Mp3TagWriter {

    fun embedAlbumArt(mp3Uri: Uri, imageBytes: ByteArray, mimeType: String, context: Context): Result<String> {
        return try {
            val tempIn = File(context.cacheDir, "mp3_in_${System.currentTimeMillis()}.mp3")
            val tempOut = File(context.cacheDir, "mp3_out_${System.currentTimeMillis()}.mp3")

            context.contentResolver.openInputStream(mp3Uri)?.use { input ->
                tempIn.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Cannot read MP3")

            val mp3 = Mp3File(tempIn)
            val id3v2Tag: ID3v2 = if (mp3.hasId3v2Tag()) {
                mp3.id3v2Tag
            } else {
                ID3v24Tag()
            }
            id3v2Tag.setAlbumImage(imageBytes, mimeType)
            mp3.id3v2Tag = id3v2Tag
            mp3.save(tempOut.absolutePath)

            context.contentResolver.openOutputStream(mp3Uri, "wt")?.use { output ->
                tempOut.inputStream().use { input -> input.copyTo(output) }
            } ?: throw Exception("Cannot write MP3 back")

            tempIn.delete()
            tempOut.delete()

            Result.success("Album art baked into MP3!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
