package com.example.chess_analyse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chess_analyse.ui.theme.Chess_AnalyseTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.state.collectAsState()

            Chess_AnalyseTheme(darkTheme = state.isDarkMode, dynamicColor = false) {
                Surface { ChessApp(vm = vm, state = state) }
            }
        }
    }
}

@Composable
fun ChessApp(vm: MainViewModel = viewModel(), state: UiState = vm.state.collectAsState().value) {
    BackHandler(enabled = state.selected != null) { vm.closeGame() }

    if (state.selected == null) {
        GameListScreen(
            state = state,
            onUsernameChange = vm::setUsername,
            onLoad = vm::loadGames,
            onMore = vm::loadMore,
            onSelect = vm::selectGame,
            onOpenSettings = { vm.toggleSettings(true) },
        )
    } else {
        AnalysisScreen(state = state, onBack = vm::closeGame, onGoTo = vm::goTo)
    }

    if (state.showSettings) {
        SettingsDialog(
            isDarkMode = state.isDarkMode,
            onDarkModeChange = vm::setDarkMode,
            onDismiss = { vm.toggleSettings(false) },
        )
    }
}

// ---------------------------------------------------------------------------
// Partienliste
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    state: UiState,
    onUsernameChange: (String) -> Unit,
    onLoad: () -> Unit,
    onMore: () -> Unit,
    onSelect: (ChessGame) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.ic_chess_analyzer),
                            contentDescription = "Chess Analyzer Logo",
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Meine Schachpartien")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = state.username,
                    onValueChange = onUsernameChange,
                    label = { Text("chess.com-Benutzername") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onLoad, enabled = !state.loading) { Text("Laden") }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.games, key = { it.url }) { game ->
                    GameCard(game, state.username, onSelect)
                }
                if (state.archivesLeft > 0 && !state.loading) {
                    item {
                        OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) {
                            Text("Ältere Partien laden")
                        }
                    }
                }
            }
        }
    }
}

fun getTimeClassIcon(timeClass: String): String = when (timeClass.lowercase()) {
    "bullet" -> "⚡"
    "blitz" -> "🔥"
    "rapid" -> "⏱️"
    "daily" -> "📅"
    else -> "♟️"
}

fun formatTimeControl(timeControl: String, timeClass: String): String {
    if (timeControl.isBlank()) return timeClass.replaceFirstChar { it.uppercase() }

    // Check for daily format like "1/86400"
    if (timeControl.contains("/")) {
        val parts = timeControl.split("/")
        val seconds = parts.getOrNull(1)?.toLongOrNull()
        if (seconds != null) {
            val days = seconds / 86400
            if (days > 0) return if (days == 1L) "1 Tag" else "$days Tage"
        }
        return "Daily"
    }

    // Check for base + increment like "180+2"
    if (timeControl.contains("+")) {
        val parts = timeControl.split("+")
        val baseSec = parts.getOrNull(0)?.toIntOrNull()
        val incSec = parts.getOrNull(1)?.toIntOrNull()
        if (baseSec != null && incSec != null) {
            val baseMin = baseSec / 60
            val baseRemSec = baseSec % 60
            val baseText = if (baseRemSec == 0) "$baseMin" else String.format(Locale.US, "%.1f", baseSec / 60.0)
            return "$baseText+$incSec min"
        }
    }

    // Pure seconds like "600", "180", "60", "300"
    val sec = timeControl.toIntOrNull()
    if (sec != null) {
        val min = sec / 60
        val remSec = sec % 60
        return if (remSec == 0) {
            "$min min"
        } else {
            val minFormatted = String.format(Locale.GERMANY, "%.1f", sec / 60.0)
            "$minFormatted min"
        }
    }

    return timeControl
}

@Composable
fun GameCard(game: ChessGame, username: String, onClick: (ChessGame) -> Unit) {
    val iAmBlack = game.black.username.equals(username.trim(), ignoreCase = true)
    val me = if (iAmBlack) game.black else game.white
    val opponent = if (iAmBlack) game.white else game.black
    val isWin = me.result == "win"
    val isLoss = opponent.result == "win"
    val outcome = when {
        isWin -> "Sieg"
        isLoss -> "Niederlage"
        else -> "Remis"
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // Soft color backgrounds (green = win, red = loss, grey = draw)
    val cardBg = when {
        isWin -> if (isDark) Color(0xFF143322) else Color(0xFFEAF5EA)
        isLoss -> if (isDark) Color(0xFF3B1A1E) else Color(0xFFFDEBED)
        else -> if (isDark) Color(0xFF242424) else Color(0xFFF4F4F4)
    }

    val outcomeTextColor = when {
        isWin -> if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
        isLoss -> if (isDark) Color(0xFFE57373) else Color(0xFFC62828)
        else -> if (isDark) Color(0xFFB0B0B0) else Color(0xFF616161)
    }

    val timeIcon = getTimeClassIcon(game.timeClass)
    val formattedTime = formatTimeControl(game.timeControl, game.timeClass)
    val timeLabel = "$timeIcon ${game.timeClass.replaceFirstChar { it.uppercase() }} ($formattedTime)"
    val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(game.endTime * 1000))

    Card(
        onClick = { onClick(game) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${game.white.username} (${game.white.rating}) – ${game.black.username} (${game.black.rating})",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    outcome,
                    fontWeight = FontWeight.Bold,
                    color = outcomeTextColor,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$timeLabel · $date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Evaluationsbalken & Analyse einer Partie
// ---------------------------------------------------------------------------

@Composable
fun EvaluationBar(
    analysis: Analysis?,
    flipped: Boolean,
    modifier: Modifier = Modifier,
) {
    val whiteRatio = when {
        analysis?.mate != null -> if (analysis.mate > 0) 1.0f else 0.0f
        analysis?.evalCp != null -> {
            val cp = analysis.evalCp.toDouble()
            val prob = 1.0 / (1.0 + 10.0.pow(-cp / 400.0))
            prob.toFloat().coerceIn(0.02f, 0.98f)
        }
        else -> 0.5f
    }

    val evalText = when {
        analysis?.mate != null -> if (analysis.mate > 0) "M${analysis.mate}" else "M${-analysis.mate}"
        analysis?.evalCp != null -> {
            val p = analysis.evalCp / 100.0
            if (p > 0) String.format(Locale.US, "+%.2f", p) else String.format(Locale.US, "%.2f", p)
        }
        else -> ""
    }

    // Top vs Bottom orientation:
    // When flipped = false: Top is Black, Bottom is White
    // When flipped = true: Top is White, Bottom is Black
    val topIsWhite = flipped
    val topFraction = if (topIsWhite) whiteRatio else (1.0f - whiteRatio)
    val bottomFraction = 1.0f - topFraction

    val topColor = if (topIsWhite) Color(0xFFF0F0F0) else Color(0xFF262421)
    val bottomColor = if (topIsWhite) Color(0xFF262421) else Color(0xFFF0F0F0)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(topFraction.coerceAtLeast(0.001f))
                        .background(topColor)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(bottomFraction.coerceAtLeast(0.001f))
                        .background(bottomColor)
                )
            }

            // Text overlay inside the larger section
            if (evalText.isNotEmpty()) {
                val whiteIsWinning = (analysis?.mate ?: analysis?.evalCp ?: 0) >= 0
                val textOnTop = if (flipped) whiteIsWinning else !whiteIsWinning
                val textColor = if (textOnTop) {
                    if (topIsWhite) Color.Black else Color.White
                } else {
                    if (topIsWhite) Color.White else Color.Black
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 4.dp, horizontal = 1.dp),
                    contentAlignment = if (textOnTop) Alignment.TopCenter else Alignment.BottomCenter,
                ) {
                    Text(
                        text = evalText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = textColor,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(state: UiState, onBack: () -> Unit, onGoTo: (Int) -> Unit) {
    val game = state.selected ?: return
    val parsed = state.parsed ?: return
    val total = parsed.ucis.size
    val ply = state.ply
    val flipped = game.black.username.equals(state.username.trim(), ignoreCase = true)
    val analysis = state.analysis
    val lastMove = if (ply > 0) parsed.ucis[ply - 1] else null
    val bestUci = analysis?.bestMoveUci?.takeIf { it.length >= 4 }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${game.white.username} – ${game.black.username}", maxLines = 1) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Zurück") } },
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvaluationBar(
                    analysis = analysis,
                    flipped = flipped,
                    modifier = Modifier
                        .width(26.dp)
                        .fillMaxHeight(),
                )
                Spacer(Modifier.width(8.dp))
                BoardView(
                    fen = parsed.fens[ply],
                    flipped = flipped,
                    lastMove = lastMove,
                    bestMove = bestUci,
                    modifier = Modifier.weight(1f),
                )
            }

            // Steuerung
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { onGoTo(0) }, enabled = ply > 0) { Text("⏮") }
                OutlinedButton(onClick = { onGoTo(ply - 1) }, enabled = ply > 0) { Text("◀") }
                OutlinedButton(onClick = { onGoTo(ply + 1) }, enabled = ply < total) { Text("▶") }
                OutlinedButton(onClick = { onGoTo(total) }, enabled = ply < total) { Text("⏭") }
            }

            // Aktueller Zug
            val moveLabel = if (ply == 0) {
                "Startstellung"
            } else {
                val number = (ply + 1) / 2
                val dots = if (ply % 2 == 1) "." else "..."
                "Zug $ply/$total: $number$dots ${parsed.sans[ply - 1]}"
            }
            Text(moveLabel, fontWeight = FontWeight.Bold)

            // Stockfish
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Stockfish", fontWeight = FontWeight.Bold)
                    when {
                        state.engineError != null ->
                            Text(state.engineError, color = MaterialTheme.colorScheme.error)
                        analysis == null -> Text(if (state.analyzing) "Analysiere …" else "–")
                        analysis.bestMoveUci.isEmpty() -> Text("Die Partie ist hier zu Ende.")
                        else -> {
                            Text("Bester Zug: ${analysis.bestMoveSan ?: analysis.bestMoveUci}  (blau markiert)")
                            Text("Bewertung: ${analysis.evalText()} (aus Sicht von Weiß, Tiefe ${analysis.depth})")
                        }
                    }
                    // Vergleich mit dem tatsächlich gespielten Zug
                    val playedSan = parsed.sans.getOrNull(ply)
                    val playedUci = parsed.ucis.getOrNull(ply)
                    if (playedSan != null && analysis != null && analysis.bestMoveUci.isNotEmpty()) {
                        if (playedUci == analysis.bestMoveUci) {
                            Text("Gespielt wurde: $playedSan – das ist der Stockfish-Zug ✓")
                        } else {
                            Text("Gespielt wurde: $playedSan")
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Einstellungs-Dialog
// ---------------------------------------------------------------------------

@Composable
fun SettingsDialog(
    isDarkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Einstellungen") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dunkles Design (Dark Mode)", fontWeight = FontWeight.Medium)
                        Text(
                            "Dunkles Farbschema aktivieren",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = onDarkModeChange,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fertig")
            }
        }
    )
}