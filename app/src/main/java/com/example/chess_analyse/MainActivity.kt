package com.example.chess_analyse

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { ChessApp() }
            }
        }
    }
}

@Composable
fun ChessApp(vm: MainViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    BackHandler(enabled = state.selected != null) { vm.closeGame() }

    if (state.selected == null) {
        GameListScreen(
            state = state,
            onUsernameChange = vm::setUsername,
            onLoad = vm::loadGames,
            onMore = vm::loadMore,
            onSelect = vm::selectGame,
        )
    } else {
        AnalysisScreen(state = state, onBack = vm::closeGame, onGoTo = vm::goTo)
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

@Composable
fun GameCard(game: ChessGame, username: String, onClick: (ChessGame) -> Unit) {
    val iAmBlack = game.black.username.equals(username.trim(), ignoreCase = true)
    val me = if (iAmBlack) game.black else game.white
    val opponent = if (iAmBlack) game.white else game.black
    val outcome = when {
        me.result == "win" -> "Sieg"
        opponent.result == "win" -> "Niederlage"
        else -> "Remis"
    }
    val date = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(game.endTime * 1000))

    Card(onClick = { onClick(game) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${game.white.username} (${game.white.rating}) – ${game.black.username} (${game.black.rating})",
                fontWeight = FontWeight.Bold,
            )
            Text("$outcome · ${game.timeClass} ${game.timeControl} · $date")
        }
    }
}

// ---------------------------------------------------------------------------
// Analyse einer Partie
// ---------------------------------------------------------------------------

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
            BoardView(fen = parsed.fens[ply], flipped = flipped, lastMove = lastMove, bestMove = bestUci)

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