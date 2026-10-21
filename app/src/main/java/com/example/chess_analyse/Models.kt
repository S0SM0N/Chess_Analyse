package com.example.chess_analyse

data class Player(val username: String, val rating: Int, val result: String)

data class ChessGame(
    val url: String,
    val pgn: String,
    val timeControl: String,
    val timeClass: String,
    val endTime: Long,
    val white: Player,
    val black: Player,
)

/** Ergebnis der Stockfish-Analyse einer Stellung. Bewertungen immer aus Sicht von Weiß. */
data class Analysis(
    val bestMoveUci: String,      // z. B. "e2e4", leer wenn die Partie zu Ende ist
    val bestMoveSan: String?,     // z. B. "e4"
    val evalCp: Int?,             // Centipawns (positiv = Weiß besser)
    val mate: Int?,               // Matt in n (positiv = Weiß setzt matt)
    val depth: Int,
) {
    fun evalText(): String = when {
        mate != null -> if (mate > 0) "Matt in $mate" else "Matt in ${-mate} (für Schwarz)"
        evalCp != null -> String.format(java.util.Locale.US, "%+.2f", evalCp / 100.0)
        else -> "–"
    }
}

data class UiState(
    val username: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val games: List<ChessGame> = emptyList(),
    val archivesLeft: Int = 0,
    val selected: ChessGame? = null,
    val parsed: ParsedGame? = null,
    val ply: Int = 0,
    val analysis: Analysis? = null,
    val analyzing: Boolean = false,
    val engineError: String? = null,
    val isDarkMode: Boolean = false,
    val showSettings: Boolean = false,
)
