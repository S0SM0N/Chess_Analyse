package com.example.chess_analyse

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("chess", Context.MODE_PRIVATE)
    private val engine = StockfishEngine(app)
    private val cache = HashMap<String, Analysis>()
    private var archiveQueue = ArrayDeque<String>()
    private var analysisJob: Job? = null
    private var analysisCounter = 0

    private val _state = MutableStateFlow(
        UiState(username = prefs.getString("username", "") ?: "")
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        if (_state.value.username.isNotBlank()) loadGames()
    }

    fun setUsername(name: String) = _state.update { it.copy(username = name) }

    // ---------- Partien laden ----------

    fun loadGames() {
        val user = _state.value.username.trim()
        if (user.isEmpty()) return
        prefs.edit().putString("username", user).apply()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, games = emptyList(), archivesLeft = 0) }
            try {
                archiveQueue = ArrayDeque(ChessComApi.archives(user).reversed()) // neueste zuerst
                _state.update { it.copy(archivesLeft = archiveQueue.size) }
                loadNextArchives()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Unbekannter Fehler") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                loadNextArchives()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Unbekannter Fehler") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Lädt Monate, bis mindestens [minGames] neue Partien da sind (oder keine Monate mehr übrig). */
    private suspend fun loadNextArchives(minGames: Int = 30) {
        var added = 0
        while (archiveQueue.isNotEmpty() && added < minGames) {
            val games = ChessComApi.gamesOfArchive(archiveQueue.removeFirst())
            added += games.size
            _state.update { it.copy(games = it.games + games, archivesLeft = archiveQueue.size) }
        }
    }

    // ---------- Analyse ----------

    fun selectGame(game: ChessGame) {
        val parsed = try {
            PgnParser.parse(game.pgn)
        } catch (e: Exception) {
            _state.update { it.copy(error = "PGN konnte nicht gelesen werden: ${e.message}") }
            return
        }
        _state.update { it.copy(selected = game, parsed = parsed, ply = 0, analysis = null, error = null) }
        analyzeCurrent()
    }

    fun closeGame() {
        analysisJob?.cancel()
        _state.update { it.copy(selected = null, parsed = null, analysis = null, analyzing = false, ply = 0) }
    }

    fun goTo(ply: Int) {
        val parsed = _state.value.parsed ?: return
        val target = ply.coerceIn(0, parsed.ucis.size)
        if (target == _state.value.ply) return
        _state.update { it.copy(ply = target) }
        analyzeCurrent()
    }

    private fun analyzeCurrent() {
        val st = _state.value
        val parsed = st.parsed ?: return
        val fen = parsed.fens[st.ply]
        analysisJob?.cancel()

        val cached = cache[fen]
        _state.update { it.copy(analysis = cached, analyzing = cached == null, engineError = null) }
        if (cached != null) return

        val id = ++analysisCounter
        analysisJob = viewModelScope.launch {
            try {
                val result = engine.analyze(fen)
                cache[fen] = result
                val now = _state.value
                if (now.parsed?.fens?.getOrNull(now.ply) == fen) {
                    _state.update { it.copy(analysis = result) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (id == analysisCounter) _state.update { it.copy(engineError = e.message) }
            } finally {
                if (id == analysisCounter) _state.update { it.copy(analyzing = false) }
            }
        }
    }

    override fun onCleared() {
        engine.close()
    }
}
