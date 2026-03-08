package com.radio.thaalam

data class NowPlayingResponse(
    val now_playing: NowPlaying
)

data class NowPlaying(
    val elapsed: Int,
    val duration: Int,
    val song: Song
)

data class Song(
    val title: String,
    val artist: String,
    val art: String?
)



data class ScheduleItem(
    val id: Int,
    val type: String,
    val name: String,
    val title: String,
    val start: String,
    val end: String,
    val is_now: Boolean
)
