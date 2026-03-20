package com.radio.thaalam


import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.palette.graphics.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.radio.thaalam.PlayerState.isPlaying
import com.radio.thaalam.PlayerState.refreshTrigger
import androidx.lifecycle.lifecycleScope
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.content.Context
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.media.audiofx.AudioEffect
import android.os.Handler
import android.os.Looper
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.radio.thaalam.RadioPlayerHolder.player


private var isAppReady = false


class MainActivity : ComponentActivity() {


    fun handlePlayback(context: Context, play: Boolean) {
        val intent = Intent(context, RadioService::class.java).apply {
            action = if (play) "ACTION_PAUSE" else "ACTION_PLAY"
        }
        context.startService(intent)
    }

    fun restartPlayback(context: Context) {
        val intent = Intent(context, RadioService::class.java).apply {
            action = "ACTION_RESTART"
        }
        context.startService(intent)
    }


    private fun observeNetworkChanges() {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                // Internet is ON → play
                handlePlayback(this@MainActivity, false)
                restartPlayback(this@MainActivity)
            }

            // Internet is OFF → pause by 5 seconds
            override fun onLost(network: Network) {
                Handler(Looper.getMainLooper()).postDelayed({
                    handlePlayback(this@MainActivity, true)
                }, 10000)


            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    override fun onResume(){
        super.onResume()
        if(!isInternetAvailable(this)&& !isAppReady){
            retryStartup()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        this.window.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        Thread.sleep(200)

        //installSplashScreen()
        observeNetworkChanges()


        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !isAppReady

        }
        retryStartup()


        setContent {
            val context = LocalContext.current

            val notificationPermissionLauncher =
                rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    // You can log or handle result here if needed
                }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val prefs = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
                    val alreadyAsked = prefs.getBoolean("notif_permission_asked", false)

                    if (!alreadyAsked) {
                        delay(5_000)

                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )

                        prefs.edit().putBoolean("notif_permission_asked", true).apply()
                    }
                }
            }




            ChooseLayout()

        }

        val intent = Intent(this, RadioService::class.java).apply {
            action = "ACTION_PLAY"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

    }


    fun isInternetAvailable(context: Context): Boolean {
        val cm =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun retryStartup(){

        lifecycleScope.launch {
            while (!isAppReady) {
                if (isInternetAvailable(this@MainActivity)) {

                    try {
                        ApiClient.api.getNowPlaying()

                        handlePlayback(this@MainActivity, false)
                        isAppReady = true
                        break

                    } catch (e: Exception) {
                        delay(2000)
                    }
                } else {
                    handlePlayback(this@MainActivity, false)
                    delay(2000)
                }
            }
        }

    }


    @Composable
    fun ChooseLayout(){
        BoxWithConstraints() {
            val screenWidth=maxWidth
            when{
                screenWidth<=360.dp->
                    CompactUI()
            }
            when{
                screenWidth>360.dp->
                    MediumUI()
            }
        }
    }


    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @SuppressLint("CoroutineCreationDuringComposition")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun CompactUI() {

        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()


        var title by remember { mutableStateOf("") }
        var artist by remember { mutableStateOf("") }
        var art by remember { mutableStateOf<String?>("") }
        var bgColor by remember { mutableStateOf(Color.Black) }
        var currentSongKey by remember { mutableStateOf("") }
        var nowOnAir by remember { mutableStateOf<ScheduleItem?>(null) }
        var showSleepSheet by remember { mutableStateOf(false) }
        var sleepSeconds: Long by remember { mutableStateOf(0) }


        var sleepJob by remember { mutableStateOf<Job?>(null) }




        // ---------- FETCH NOW PLAYING API CALL ----------
        LaunchedEffect(refreshTrigger.value) {
            // LaunchedEffect(Unit) {
            if (!isPlaying.value) return@LaunchedEffect
            while (isPlaying.value) {
                try {
                    val data = ApiClient.api.getNowPlaying()[0]
                    val newKey = data.now_playing.song.title + data.now_playing.song.artist

                    val schedule = ApiClient.api.getSchedule(6)
                    nowOnAir = schedule.firstOrNull { it.is_now }

                    if (newKey != currentSongKey) {
                        currentSongKey = newKey

                        // update metadata
                        title = data.now_playing.song.title
                        artist = data.now_playing.song.artist
                        art = data.now_playing.song.art


                        val intent = Intent(context, RadioService::class.java).apply {
                            putExtra("TITLE", title)
                            putExtra("ARTIST", artist)
                            putExtra("ART", art)
                        }
                        context.startService(intent)

                        val schedule = ApiClient.api.getSchedule(6)
                        nowOnAir = schedule.firstOrNull { it.is_now }
                    }

                } catch (_: Exception) {
                    //  e.printStackTrace()
                }

                delay(15000) // API polling interval
            }
        }

        // ---------- COLOR EXTRACTION ----------
        LaunchedEffect(art) {
            art?.let {
                val loader = ImageLoader(context)
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(it)
                        .allowHardware(false)
                        .build()
                )

                val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp?.let {
                    Palette.from(it).generate { palette ->
                        bgColor = Color(
                            palette?.darkVibrantSwatch?.rgb
                                ?: palette?.vibrantSwatch?.rgb
                                ?: palette?.dominantSwatch?.rgb
                                ?: 0xFF000000.toInt()
                        )
                    }
                }
            }
        }


        @RequiresApi(Build.VERSION_CODES.O)
        fun formatTime(time: String): String {
            return try {
                val parsed = OffsetDateTime.parse(time).plusMinutes(2)
                parsed.format(
                    DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                ).uppercase()
            } catch (e: Exception) {
                time
            }
        }


        //sleep
        fun startSleepTimer(minutes: Int) {
            sleepJob?.cancel()

            sleepSeconds = minutes * 60L
            sleepJob = CoroutineScope(Dispatchers.Main).launch {
                while (sleepSeconds > 0) {
                    delay(1000)
                    sleepSeconds--
                }

                //  ONLY HERE stop playback
                handlePlayback(context,true)
                Toast.makeText(
                    context,
                    "Time's up. Playback stopped",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // ---------- UI ----------
        Box(
            modifier = Modifier

                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(bgColor, Color.Black)))
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))



                // ---- SCHEDULE BLOCK (ALWAYS VISIBLE) ----

                    Text(
                        text = nowOnAir?.name ?: "",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )

                   // Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (nowOnAir != null)
                            "${formatTime(nowOnAir!!.start)} - ${formatTime(nowOnAir!!.end)}"
                        else
                            "",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp
                    )
                


Spacer(Modifier.height(8.dp))

AsyncImage(
    model = art,
    contentDescription = null,
    modifier = Modifier
        .size(380.dp)
        .clip(RoundedCornerShape(18.dp))
)

Spacer(Modifier.height(40.dp))


Text(
    text = title,
    color = Color.White,
    style = MaterialTheme.typography.titleLarge,
    maxLines = 1,
    modifier = Modifier
        .fillMaxWidth()
        .basicMarquee(
            iterations = if (isPlaying.value) Int.MAX_VALUE else 0
        )
)


Text(
    artist,
    color = Color.White.copy(alpha = 0.7f),
    maxLines = 1,
    modifier = Modifier.fillMaxWidth()
        .basicMarquee(
            if (isPlaying.value) Int.MAX_VALUE else 0


        )
)



Spacer(Modifier.height(30.dp))


val glowAlpha by rememberInfiniteTransition().animateFloat(
    initialValue = 0.3f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        animation = tween(1200),
        repeatMode = RepeatMode.Reverse
    ),
    label = "glow"
)

val finalAlpha = if (isPlaying.value) glowAlpha else 0.3f

Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
) {

    Box(
        modifier = Modifier
            .weight(1.5f)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.White.copy(alpha = finalAlpha),
                        Color.Transparent
                    )
                )
            )
    )

    Text(
        text = "LIVE",
        color = Color.White,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 14.dp)
    )

    Box(
        modifier = Modifier
            .weight(1.5f)
            .height(5.dp)
            .clip(RoundedCornerShape(50))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = finalAlpha)
                    )
                )
            )
    )
}


Spacer(Modifier.height(20.dp))

Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),
    verticalAlignment = Alignment.CenterVertically
) {


    // EQUALIZER BUTTON (LEFT)
    IconButton(

        onClick = {

            val sessionId = player?.audioSessionId ?: run {
                Toast.makeText(context, "Player not ready", Toast.LENGTH_SHORT).show()
                return@IconButton
            }

            if (sessionId == C.AUDIO_SESSION_ID_UNSET) {
                Toast.makeText(context, "Play audio once to enable equalizer", Toast.LENGTH_SHORT)
                    .show()
                return@IconButton
            }

            try {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)

                }
                startActivityForResult(intent,13)
                //context.startActivity(intent)

            } catch (e: Exception) {
                Toast.makeText(context, "System equalizer not available", Toast.LENGTH_SHORT).show()
            }
        }

        ,
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Equalizer,
            contentDescription = "Equalizer",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }


    Spacer(modifier = Modifier.weight(1f))


 //Play/pause
    IconButton(

        onClick = {
            if (isPlaying.value) {
                handlePlayback(context, true)
            } else {
                handlePlayback(context, false)

            }

        }, modifier = Modifier.size(88.dp)

    )

    {
        Icon(


            imageVector = if (isPlaying.value) {
                Icons.Default.PauseCircleFilled
                //Icons.Default.Pause
            } else
                Icons.Default.PlayCircleFilled,
            contentDescription = "Play / Pause",
            tint = Color.White,
            modifier = Modifier.size(80.dp)
        )
    }

    Spacer(modifier = Modifier.weight(1f))



    //sleepbutton
    IconButton(
        onClick = {
            showSleepSheet = true
        }
    ) {
        Icon(
            imageVector = Icons.Default.NightsStay,
            contentDescription = "Sleep Timer",
            tint = Color.White
        )
    }


}

}
//Ui SLeep

            if (showSleepSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSleepSheet = false },
                    containerColor = Color(0xFF121212)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "Sleep Timer",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(20.dp))

                        if (sleepSeconds > 0) {
                            Text(
                                text = "Ends in ${sleepSeconds / 60}:${(sleepSeconds % 60).toString().padStart(2, '0')}",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        val sleepOptions = listOf(
                            "Timer Off" to 0,
                            "5 minutes" to 5,
                            "15 minutes" to 15,
                            "30 minutes" to 30,
                            "45 minutes" to 45,
                            "60 minutes" to 60
                        )

                        sleepOptions.forEachIndexed { index, (label, minutes) ->

                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sleepJob?.cancel()

                                        if (minutes == 0) {
                                            sleepSeconds = 0
                                            Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                                        } else {
                                            startSleepTimer(minutes)
                                            Toast.makeText(
                                                context,
                                                "Sleep timer set for $minutes minutes",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        showSleepSheet = false
                                    }
                                    .padding(vertical = 14.dp),
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            // Divider between options (not after last)
                            if (index != sleepOptions.lastIndex) {
                                Divider(
                                    color = Color.White.copy(alpha = 0.25f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }


}



}



    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @SuppressLint("CoroutineCreationDuringComposition", "UnsafeOptInUsageError")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MediumUI() {

        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()


        var title by remember { mutableStateOf("") }
        var artist by remember { mutableStateOf("") }
        var art by remember { mutableStateOf<String?>("") }
        var bgColor by remember { mutableStateOf(Color.Black) }
        var currentSongKey by remember { mutableStateOf("") }
        var nowOnAir by remember { mutableStateOf<ScheduleItem?>(null) }
        var showSleepSheet by remember { mutableStateOf(false) }
        var sleepSeconds: Long by remember { mutableStateOf(0) }


        var sleepJob by remember { mutableStateOf<Job?>(null) }




        // ---------- FETCH NOW PLAYING API CALL ----------
        LaunchedEffect(refreshTrigger.value) {
            // LaunchedEffect(Unit) {
            if (!isPlaying.value) return@LaunchedEffect
            while (isPlaying.value) {
                try {
                    val data = ApiClient.api.getNowPlaying()[0]
                    val newKey = data.now_playing.song.title + data.now_playing.song.artist

                    val schedule = ApiClient.api.getSchedule(6)
                    nowOnAir = schedule.firstOrNull { it.is_now }

                    if (newKey != currentSongKey) {
                        currentSongKey = newKey

                        // update metadata
                        title = data.now_playing.song.title
                        artist = data.now_playing.song.artist
                        art = data.now_playing.song.art


                        val intent = Intent(context, RadioService::class.java).apply {
                            putExtra("TITLE", title)
                            putExtra("ARTIST", artist)
                            putExtra("ART", art)
                        }
                        context.startService(intent)

                        val schedule = ApiClient.api.getSchedule(6)
                        nowOnAir = schedule.firstOrNull { it.is_now }
                    }

                } catch (_: Exception) {
                    //  e.printStackTrace()
                }

                delay(15000) // API polling interval
            }
        }

        // ---------- COLOR EXTRACTION ----------
        LaunchedEffect(art) {
            art?.let {
                val loader = ImageLoader(context)
                val result = loader.execute(
                    ImageRequest.Builder(context)
                        .data(it)
                        .allowHardware(false)
                        .build()
                )

                val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                bmp?.let {
                    Palette.from(it).generate { palette ->
                        bgColor = Color(
                            palette?.darkVibrantSwatch?.rgb
                                ?: palette?.vibrantSwatch?.rgb
                                ?: palette?.dominantSwatch?.rgb
                                ?: 0xFF000000.toInt()
                        )
                    }
                }
            }
        }


        @RequiresApi(Build.VERSION_CODES.O)
        fun formatTime(time: String): String {
            return try {
                val parsed = OffsetDateTime.parse(time).plusMinutes(2)
                parsed.format(
                    DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
                ).uppercase()
            } catch (e: Exception) {
                time
            }
        }


        //sleep
        fun startSleepTimer(minutes: Int) {
            sleepJob?.cancel()

            sleepSeconds = minutes * 60L

            sleepJob = CoroutineScope(Dispatchers.Main).launch {
                while (sleepSeconds > 0) {
                    delay(1000)
                    sleepSeconds--
                }

                //  ONLY HERE stop playback
                handlePlayback(context,true)
                Toast.makeText(
                    context,
                    "Time's up. Playback stopped",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // ---------- UI ----------
        Box(
            modifier = Modifier

                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(bgColor, Color.Black)))
                .verticalScroll(rememberScrollState())
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(75.dp))



                // ---- SCHEDULE BLOCK (ALWAYS VISIBLE) ----

                Text(
                    text = nowOnAir?.name ?: "",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )


                Text(
                    text = if (nowOnAir != null)
                        "${formatTime(nowOnAir!!.start)} - ${formatTime(nowOnAir!!.end)}"
                    else
                        "",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )



                Spacer(Modifier.height(22.dp))

                AsyncImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier
                        .size(380.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(Modifier.height(40.dp))


                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(
                            iterations = if (isPlaying.value) Int.MAX_VALUE else 0
                        )
                )


                Text(
                    artist,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth()
                        .basicMarquee(
                            if (isPlaying.value) Int.MAX_VALUE else 0


                        )
                )



                Spacer(Modifier.height(30.dp))


                val glowAlpha by rememberInfiniteTransition().animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glow"
                )

                val finalAlpha = if (isPlaying.value) glowAlpha else 0.3f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.White.copy(alpha = finalAlpha),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .height(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = finalAlpha)
                                    )
                                )
                            )
                    )
                }


                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {


                    // EQUALIZER BUTTON (LEFT)
                    IconButton(

                        onClick = {

                            val sessionId = player?.audioSessionId ?: run {
                                Toast.makeText(context, "Player not ready", Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            if (sessionId == C.AUDIO_SESSION_ID_UNSET) {
                                Toast.makeText(context, "Play audio once to enable equalizer", Toast.LENGTH_SHORT)
                                    .show()
                                return@IconButton
                            }

                            try {
                                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                                }

                                startActivityForResult(intent,13)

                            } catch (e: Exception) {
                                Toast.makeText(context, "System equalizer not available", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = "Equalizer",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }


                    Spacer(modifier = Modifier.weight(1f))


                    //Play/pause
                    IconButton(

                        onClick = {
                            if (isPlaying.value) {
                                handlePlayback(context, true)
                            } else {
                                handlePlayback(context, false)

                            }

                        }, modifier = Modifier.size(88.dp)

                    )

                    {
                        Icon(


                            imageVector = if (isPlaying.value) {
                                Icons.Default.PauseCircleFilled
                                //Icons.Default.Pause
                            } else
                                Icons.Default.PlayCircleFilled,
                            contentDescription = "Play / Pause",
                            tint = Color.White,
                            modifier = Modifier.size(80.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))



                    //sleepbutton
                    IconButton(
                        onClick = {
                            showSleepSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.NightsStay,
                            contentDescription = "Sleep Timer",
                            tint = Color.White
                        )
                    }


                }




            }
            //Ui SLeep

            if (showSleepSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showSleepSheet = false },
                    containerColor = Color(0xFF121212)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "Sleep Timer",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(20.dp))

                        if (sleepSeconds > 0) {
                            Text(
                                text = "Ends in ${sleepSeconds / 60}:${(sleepSeconds % 60).toString().padStart(2, '0')}",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        val sleepOptions = listOf(
                            "Timer Off" to 0,
                            "5 minutes" to 5,
                            "15 minutes" to 15,
                            "30 minutes" to 30,
                            "45 minutes" to 45,
                            "60 minutes" to 60
                        )

                        sleepOptions.forEachIndexed { index, (label, minutes) ->

                            Text(
                                text = label,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sleepJob?.cancel()

                                        if (minutes == 0) {
                                            sleepSeconds = 0
                                            Toast.makeText(context, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
                                        } else {
                                            startSleepTimer(minutes)
                                            Toast.makeText(
                                                context,
                                                "Sleep timer set for $minutes minutes",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                        showSleepSheet = false
                                    }
                                    .padding(vertical = 14.dp),
                                color = Color.White,
                                fontSize = 18.sp
                            )

                            // Divider between options (not after last)
                            if (index != sleepOptions.lastIndex) {
                                Divider(
                                    color = Color.White.copy(alpha = 0.25f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }


        }
    }
}









