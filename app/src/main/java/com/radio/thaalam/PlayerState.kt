package com.radio.thaalam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

object PlayerState {
    //var isPlaying: Boolean= true
    val isPlaying= mutableStateOf(true)

    var refreshTrigger = mutableStateOf(true)

}
