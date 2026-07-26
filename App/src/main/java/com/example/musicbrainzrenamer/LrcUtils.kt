package com.example.musicbrainzrenamer

data class LrcLine(val timeMs: Long, val text: String)

object LrcUtils {
    private val LRC_REGEX = "\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)".toRegex()

    fun parse(content: String): List<LrcLine> {
        return content.lines().mapNotNull { line ->
            val match = LRC_REGEX.find(line.trim()) ?: return@mapNotNull null
            val min = match.groupValues[1].toLong()
            val sec = match.groupValues[2].toLong()
            val msRaw = match.groupValues[3]
            val ms = msRaw.toLong() * if (msRaw.length == 2) 10 else 1
            LrcLine(min * 60000 + sec * 1000 + ms, match.groupValues[4])
        }
    }

    fun toText(lines: List<LrcLine>): String = lines.joinToString("
") { formatLine(it) }

    fun formatLine(line: LrcLine): String {
        val min = line.timeMs / 60000
        val sec = (line.timeMs % 60000) / 1000
        val ms = (line.timeMs % 1000) / 10
        return String.format("[%02d:%02d.%02d]%s", min, sec, ms, line.text)
    }

    fun applyOffset(lines: List<LrcLine>, offsetMs: Long): List<LrcLine> {
        return lines.map { it.copy(timeMs = (it.timeMs + offsetMs).coerceAtLeast(0)) }
    }
}
