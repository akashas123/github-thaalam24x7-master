package com.radio.thaalam

import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("api/nowplaying")
    suspend fun getNowPlaying(): List<NowPlayingResponse>

    @GET("api/station/{stationId}/schedule")
    suspend fun getSchedule(
        @Path("stationId") stationId: Int
    ): List<ScheduleItem>


}


