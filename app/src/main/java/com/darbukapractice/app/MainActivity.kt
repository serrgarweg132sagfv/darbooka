@file:OptIn(ExperimentalMaterial3Api::class)

package com.darbukapractice.app

import android.content.Context
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.*
import com.darbukapractice.app.data.Exercises
import com.darbukapractice.app.db.AppDatabase
import com.darbukapractice.app.db.SessionEntity
import com.darbukapractice.app.theme.DarbukaTheme
import com.darbukapractice.app.utils.Jalali
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.*

class PracticeViewModel(private val context: Context) : ViewModel() {
    private val dao = AppDatabase.get(context).sessions()
    private val _sessions = MutableStateFlow<List<SessionEntity>>(emptyList())
    val sessions: StateFlow<List<SessionEntity>> = _sessions
    private var timerJob: Job? = null
    var timerState by mutableStateOf(TimerState.IDLE); private set
    var elapsedSeconds by mutableLongStateOf(0L); private set
    private var startedAt = 0L
    private var accumulated = 0L

    init { refresh() }
    fun refresh() = viewModelScope.launch { _sessions.value = dao.all() }
    fun delete(session: SessionEntity) = viewModelScope.launch { dao.delete(session); refresh() }

    fun startTimer() {
        if (timerState == TimerState.RUNNING) return
        if (timerState == TimerState.IDLE || timerState == TimerState.STOPPED) {
            accumulated = 0L; elapsedSeconds = 0L
        }
        startedAt = System.nanoTime()
        timerState = TimerState.RUNNING
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timerState == TimerState.RUNNING) {
                elapsedSeconds = accumulated + ((System.nanoTime() - startedAt) / 1_000_000_000L)
                delay(250)
            }
        }
    }
    fun pauseTimer() {
        if (timerState != TimerState.RUNNING) return
        accumulated += (System.nanoTime() - startedAt) / 1_000_000_000L
        elapsedSeconds = accumulated
        timerState = TimerState.PAUSED
        timerJob?.cancel()
    }
    fun resumeTimer() {
        if (timerState != TimerState.PAUSED) return
        startedAt = System.nanoTime(); timerState = TimerState.RUNNING
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (timerState == TimerState.RUNNING) {
                elapsedSeconds = accumulated + ((System.nanoTime() - startedAt) / 1_000_000_000L)
                delay(250)
            }
        }
    }
    fun finishSession(exerciseId: Int, bpm: Int) {
        val final = if (timerState == TimerState.RUNNING) {
            accumulated + ((System.nanoTime() - startedAt) / 1_000_000_000L)
        } else elapsedSeconds
        timerJob?.cancel(); timerState = TimerState.STOPPED; elapsedSeconds = final
        if (final > 0L) viewModelScope.launch {
            dao.insert(SessionEntity(exerciseId = exerciseId, timestamp = System.currentTimeMillis(), bpm = bpm, durationSeconds = final))
            refresh()
        }
    }
    fun resetTimer() { timerJob?.cancel(); timerState = TimerState.IDLE; elapsedSeconds = 0L; accumulated = 0L }
    override fun onCleared() { timerJob?.cancel(); super.onCleared() }
}

enum class TimerState { IDLE, RUNNING, PAUSED, STOPPED }
enum class Tab { EXERCISES, METRONOME, STATS, SETTINGS }

data class SoundChoice(val id: Int, val label: String)

class MainActivity : ComponentActivity() {
    private lateinit var vm: PracticeViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        vm = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PracticeViewModel(applicationContext) as T
        })[PracticeViewModel::class.java]
        setContent { DarbukaTheme { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { DarbukaApp(vm) } } }
    }
}

@Composable
fun DarbukaApp(vm: PracticeViewModel) {
    var tab by remember { mutableStateOf(Tab.EXERCISES) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val sessions by vm.sessions.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Darbuka Practice", fontWeight = FontWeight.Bold) }, navigationIcon = { Icon(Icons.Default.GraphicEq, null) }) },
        bottomBar = {
            NavigationBar {
                listOf(
                    Tab.EXERCISES to ("تمرین‌ها" to Icons.Default.MenuBook),
                    Tab.METRONOME to ("مترونوم" to Icons.Default.MusicNote),
                    Tab.STATS to ("گراف / آمار" to Icons.Default.ShowChart),
                    Tab.SETTINGS to ("تنظیمات" to Icons.Default.Settings)
                ).forEach { (t, pair) ->
                    NavigationBarItem(selected = tab == t, onClick = { tab = t; selected = null }, icon = { Icon(pair.second, null) }, label = { Text(pair.first) })
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad).padding(horizontal = 14.dp, vertical = 8.dp)) {
            when {
                selected != null -> ExerciseScreen(selected!!, sessions, vm) { selected = null }
                tab == Tab.EXERCISES -> ExerciseList(sessions) { selected = it }
                tab == Tab.METRONOME -> MetronomeScreen()
                tab == Tab.STATS -> StatsScreen(sessions)
                tab == Tab.SETTINGS -> SettingsScreen()
            }
        }
    }
}

@Composable
fun ExerciseList(sessions: List<SessionEntity>, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(Modifier.width(360.dp).fillMaxHeight(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("کتاب تمرین داربوکا", fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("۳۳ تمرین ثابت", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(Exercises.all) { e ->
                        val count = sessions.count { it.exerciseId == e.id }
                        Card(Modifier.fillMaxWidth().clickable { onSelect(e.id) }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) { Text(e.number.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold) }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text("مترونوم پیشنهادی: ${e.bpmMin}–${e.bpmMax}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (count > 0) AssistChip(onClick = { }, label = { Text("$count جلسه") })
                            }
                        }
                    }
                }
            }
        }
        Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.TouchApp, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp)); Text("یک تمرین را انتخاب کنید", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("نت‌های تمرین، مترونوم و تاریخچه در صفحه اختصاصی هر تمرین نمایش داده می‌شود.", color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun ExerciseScreen(id: Int, sessions: List<SessionEntity>, vm: PracticeViewModel, onBack: () -> Unit) {
    val e = Exercises.all.first { it.id == id }
    val prefs = remember { contextForPrefs(LocalContext.current) }
    var bpm by remember(id) { mutableIntStateOf(prefs.getInt("bpm_$id", e.bpmMin.coerceIn(40, 250)).coerceIn(40, 250)) }
    var volume by remember { mutableFloatStateOf(prefs.getFloat("volume", .82f)) }
    var sound by remember { mutableIntStateOf(prefs.getInt("sound", 0).coerceIn(0, 2)) }
    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf<SessionEntity?>(null) }
    val context = LocalContext.current
    val metronome = remember { MetronomeEngine(context) }
    val elapsed = vm.elapsedSeconds
    DisposableEffect(Unit) { onDispose { metronome.release() } }
    LaunchedEffect(running, paused, bpm, volume, sound) {
        if (running && !paused) metronome.loop(bpm, volume, sound) { beat -> }
        else metronome.stop()
    }
    LaunchedEffect(running, paused) {
        when {
            running && !paused -> vm.startTimer()
            running && paused -> vm.pauseTimer()
        }
    }
    if (showDelete != null) ConfirmDelete(showDelete!!, { vm.delete(showDelete!!); showDelete = null }, { showDelete = null })
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { metronome.stop(); if (running) vm.finishSession(e.id, bpm); onBack() }) { Icon(Icons.Default.ArrowBack, null) }
                    Column(Modifier.weight(1f)) { Text("تمرین ${e.number}", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text(e.title, fontSize = 16.sp) }
                    Column(horizontalAlignment = Alignment.End) { Text("مترونوم پیشنهادی", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline); Text("${e.bpmMin}–${e.bpmMax} BPM", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
                    val resId = context.resources.getIdentifier("exercise_${e.number.toString().padStart(2, '0')}", "drawable", context.packageName)
                    Image(painterResource(resId), contentDescription = "نت‌های تمرین ${e.number}", modifier = Modifier.fillMaxWidth().padding(20.dp), contentScale = ContentScale.Fit)
                }
            }
        }
        Card(Modifier.width(330.dp).fillMaxHeight(), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("مترونوم تمرین", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("4/4", color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("$bpm BPM", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Knob(bpm.toFloat(), 40f, 250f, 270f) { bpm = it.roundToInt().coerceIn(40, 250); prefs.edit().putInt("bpm_$id", bpm).apply() }
                Text("صدای مترونوم", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("چوبی", "کلیک", "زنگ").forEachIndexed { i, s -> FilterChip(selected = sound == i, onClick = { sound = i; prefs.edit().putInt("sound", i).apply() }, label = { Text(s) }) } }
                Spacer(Modifier.height(4.dp))
                Text("ولوم ${round(volume * 100).toInt()}٪", fontSize = 13.sp)
                Knob(volume, 0f, 1f, 270f) { volume = it.coerceIn(0f, 1f); prefs.edit().putFloat("volume", volume).apply() }
                Text(formatDuration(elapsed), fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Button(enabled = !running, onClick = { running = true; paused = false }) { Text("شروع") }
                    Button(enabled = running, onClick = { paused = !paused }) { Text(if (paused) "ادامه" else "مکث") }
                    OutlinedButton(enabled = running, onClick = { running = false; paused = false; metronome.stop(); vm.finishSession(e.id, bpm) }) { Text("پایان") }
                }
                Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(6.dp))
                Text("جلسات تمرین", fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(sessions.filter { it.exerciseId == e.id }) { s ->
                        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(Jalali.format(s.timestamp), fontWeight = FontWeight.SemiBold); Text("${s.bpm} BPM  •  ${s.durationSeconds / 60} دقیقه") }
                                IconButton(onClick = { showDelete = s }) { Icon(Icons.Default.DeleteOutline, null) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetronomeScreen() {
    val context = LocalContext.current
    val prefs = remember { contextForPrefs(context) }
    var bpm by remember { mutableIntStateOf(prefs.getInt("global_bpm", 80).coerceIn(40, 250)) }; var volume by remember { mutableFloatStateOf(prefs.getFloat("volume", .82f)) }; var sound by remember { mutableIntStateOf(prefs.getInt("sound", 0).coerceIn(0, 2)) }; var running by remember { mutableStateOf(false) }
    val engine = remember { MetronomeEngine(context) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    LaunchedEffect(running, bpm, volume, sound) { if (running) engine.loop(bpm, volume, sound) { } else engine.stop() }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("مترونوم", fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("4/4 فقط", color = MaterialTheme.colorScheme.primary); Text("$bpm BPM", fontSize = 28.sp); Knob(bpm.toFloat(), 40f, 250f, 270f) { bpm = it.roundToInt().coerceIn(40, 250); prefs.edit().putInt("global_bpm", bpm).apply() }; Row { Button(onClick = { running = !running }) { Text(if (running) "توقف" else "شروع") }; Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = { engine.playClick(bpm, volume, sound, 0) }) { Text("تست صدا") } } }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("ولوم مترونوم", fontSize = 20.sp); Knob(volume, 0f, 1f, 270f) { volume = it; prefs.edit().putFloat("volume", volume).apply() }; Text("${round(volume * 100).toInt()}٪") ; Spacer(Modifier.height(12.dp)); Text("صدای انتخابی", fontWeight = FontWeight.SemiBold); Row { listOf("چوبی", "کلیک", "زنگ").forEachIndexed { i, s -> FilterChip(selected = sound == i, onClick = { sound = i; prefs.edit().putInt("sound", i).apply() }, label = { Text(s) }) } } }
    }
}

@Composable
fun StatsScreen(sessions: List<SessionEntity>) {
    val total = sessions.sumOf { it.durationSeconds }; val maxBpm = sessions.maxOfOrNull { it.bpm } ?: 0; val days = sessions.map { Jalali.format(it.timestamp) }.distinct().size; val practiced = sessions.map { it.exerciseId }.distinct().size
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.width(330.dp).fillMaxHeight(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("گراف و آمار", fontSize = 28.sp, fontWeight = FontWeight.Bold); StatLine("کل زمان تمرین", formatDuration(total)); StatLine("تعداد جلسات", sessions.size.toString()); StatLine("روزهای فعال", days.toString()); StatLine("بیشترین BPM", maxBpm.toString()); StatLine("تمرین‌های انجام‌شده", "$practiced / 33"); Spacer(Modifier.height(8.dp)); Text("پیشرفت کلی", fontWeight = FontWeight.Bold); val progress = if (sessions.isEmpty()) 0f else (practiced / 33f * .55f + min(1f, maxBpm / 140f) * .25f + min(1f, total / 7200f) * .20f); LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, Modifier.fillMaxWidth()); Text("${round(progress * 100).toInt()}٪") } }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GraphCard("روند BPM جلسات", sessions.map { it.bpm.toFloat() }, "BPM", Modifier.weight(1f))
            GraphCard("زمان تمرین هر جلسه", sessions.map { it.durationSeconds / 60f }, "دقیقه", Modifier.weight(1f))
        }
    }
}

@Composable fun StatLine(label: String, value: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) } } }

@Composable fun GraphCard(title: String, values: List<Float>, unit: String, modifier: Modifier = Modifier) { Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("$unit • بر اساس جلسات ثبت‌شده", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline); if (values.size < 2) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("برای نمایش نمودار حداقل دو جلسه ثبت کنید.") } else MiniChart(values) } } }

@Composable fun MiniChart(values: List<Float>) { val primary = MaterialTheme.colorScheme.primary; Canvas(Modifier.fillMaxWidth().fillMaxHeight().padding(16.dp)) { val lo = values.minOrNull() ?: 0f; val hi = max((values.maxOrNull() ?: 1f), lo + 1f); val p = androidx.compose.ui.graphics.Path(); values.forEachIndexed { i, v -> val x = if (values.lastIndex == 0) 0f else i.toFloat() / values.lastIndex * size.width; val y = size.height - (v - lo) / (hi - lo) * size.height; if (i == 0) p.moveTo(x, y) else p.lineTo(x, y); drawCircle(primary, 5.dp.toPx(), Offset(x, y)) }; drawPath(p, primary, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)) } }

@Composable fun SettingsScreen() { Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("تنظیمات", fontSize = 30.sp, fontWeight = FontWeight.Bold); SettingCard("حالت برنامه", "رابط کاربری برای استفاده افقی گوشی و تبلت بهینه شده است."); SettingCard("حافظه", "جلسات تمرین و آمار به‌صورت محلی روی دستگاه ذخیره می‌شوند."); SettingCard("حریم خصوصی", "بدون حساب کاربری، بدون سرور و بدون ارسال داده تمرین."); SettingCard("نسخه", "1.0.0") } }
@Composable fun SettingCard(title: String, text: String) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(text, color = MaterialTheme.colorScheme.outline) } } }

@Composable
fun Knob(value: Float, min: Float, max: Float, sweep: Float, onChange: (Float) -> Unit) {
    val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f); val active = sweep * fraction
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    Box(Modifier.size(112.dp).pointerInput(min, max) { detectDragGestures { _, drag -> onChange((value + drag.x * (max - min) / 260f).coerceIn(min, max)) } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { val stroke = 12.dp.toPx(); drawArc(surfaceVariant, 135f, sweep, false, style = Stroke(stroke, cap = StrokeCap.Round)); drawArc(primary, 135f, active, false, style = Stroke(stroke, cap = StrokeCap.Round)); val a = Math.toRadians((135 + active).toDouble()); val r = size.minDimension / 2f - 22.dp.toPx(); drawCircle(primary, 7.dp.toPx(), Offset(size.width / 2 + cos(a).toFloat() * r, size.height / 2 + sin(a).toFloat() * r)) }
    }
}

@Composable fun ConfirmDelete(session: SessionEntity, yes: () -> Unit, no: () -> Unit) { AlertDialog(onDismissRequest = no, title = { Text("حذف جلسه تمرین") }, text = { Text("آیا از حذف جلسه ${Jalali.format(session.timestamp)} مطمئن هستید؟") }, confirmButton = { TextButton(onClick = yes) { Text("حذف") } }, dismissButton = { TextButton(onClick = no) { Text("لغو") } }) }

fun contextForPrefs(context: Context) = context.getSharedPreferences("darbuka_settings", Context.MODE_PRIVATE)

fun formatDuration(s: Long): String { val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60; return if (h > 0) "%02d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec) }

class MetronomeEngine(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        ).build()
    private val soundIds = intArrayOf(
        pool.load(context, R.raw.click_wood, 1),
        pool.load(context, R.raw.click_soft, 1),
        pool.load(context, R.raw.click_bell, 1)
    )
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    private var job: Job? = null

    fun loop(bpm: Int, volume: Float, sound: Int, onBeat: (Int) -> Unit) {
        job?.cancel()
        pool.autoResume()
        job = scope.launch {
            var beat = 0
            while (true) {
                playClick(bpm, volume, sound, beat)
                onBeat(beat)
                beat = (beat + 1) % 4
                delay(60000L / bpm.coerceIn(40, 250))
            }
        }
    }

    fun playClick(@Suppress("UNUSED_PARAMETER") bpm: Int, volume: Float, sound: Int, beat: Int) {
        pool.autoResume()
        val v = (volume.coerceIn(0f, 1f) * if (beat == 0) 1f else .78f).coerceIn(0f, 1f)
        pool.play(soundIds[sound.coerceIn(0, soundIds.lastIndex)], v, v, 1, 0, 1f)
    }

    fun stop() { job?.cancel(); job = null; pool.autoPause() }
    fun release() { stop(); scope.cancel(); pool.release() }
}

