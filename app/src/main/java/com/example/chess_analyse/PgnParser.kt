package com.example.chess_analyse

import com.github.bhlangonijr.chesslib.Board
import com.github.bhlangonijr.chesslib.move.MoveList

/**
 * fens[0] = Startstellung, fens[i] = Stellung nach dem i-ten Halbzug.
 * sans[i] / ucis[i] = der (i+1)-te Halbzug.
 */
data class ParsedGame(val fens: List<String>, val sans: List<String>, val ucis: List<String>)

object PgnParser {
    private val results = setOf("1-0", "0-1", "1/2-1/2", "*")

    fun parse(pgn: String): ParsedGame {
        // Kopfzeilen [Event "..."] entfernen
        val movetext = pgn.lineSequence()
            .filterNot { it.trimStart().startsWith("[") }
            .joinToString(" ")

        val tokens = movetext
            .replace(Regex("\\{[^}]*\\}"), " ")   // Kommentare, z. B. {[%clk 0:09:58]}
            .replace(Regex("\\([^)]*\\)"), " ")   // Varianten
            .replace(Regex("\\$\\d+"), " ")       // NAG-Symbole
            .split(Regex("\\s+"))
            .map { it.replace(Regex("^\\d+\\.(\\.\\.)?"), "").replace(Regex("[!?]+$"), "") }
            .filter { it.isNotEmpty() && it !in results }

        val list = MoveList()
        list.loadFromSan(tokens.joinToString(" "))

        val board = Board()
        val fens = mutableListOf(board.fen)
        val sans = mutableListOf<String>()
        val ucis = mutableListOf<String>()
        for ((i, move) in list.withIndex()) {
            board.doMove(move)
            fens += board.fen
            ucis += move.toString().lowercase()
            sans += tokens.getOrElse(i) { move.toString().lowercase() }
        }
        return ParsedGame(fens, sans, ucis)
    }

    /** Wandelt einen UCI-Zug (z. B. "g1f3") in der gegebenen Stellung in SAN ("Nf3") um. */
    fun uciToSan(fen: String, uci: String): String? = runCatching {
        val board = Board()
        board.loadFromFen(fen)
        val move = board.legalMoves().first { it.toString().lowercase() == uci.lowercase() }
        val list = MoveList(fen)
        list.add(move)
        list.toSanArray().first()
    }.getOrNull()
}
