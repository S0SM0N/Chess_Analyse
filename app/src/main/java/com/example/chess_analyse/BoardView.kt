package com.example.chess_analyse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity

private val LIGHT = Color(0xFFEEEED2)
private val DARK = Color(0xFF769656)
private val LAST_LIGHT = Color(0xFFF6F669)
private val LAST_DARK = Color(0xFFBACA2B)
private val BEST = Color(0xFF4FC3F7)

private fun parseFen(fen: String): Array<CharArray> {
    val rows = fen.substringBefore(' ').split('/')
    return Array(8) { r ->
        val line = CharArray(8) { ' ' }
        var f = 0
        for (ch in rows[r]) {
            if (ch.isDigit()) f += ch.digitToInt() else line[f++] = ch
        }
        line
    }
}

private fun squaresOf(uci: String?): Set<String> =
    if (uci != null && uci.length >= 4) setOf(uci.substring(0, 2), uci.substring(2, 4)) else emptySet()

private fun glyph(p: Char): String = when (p) {
    'K' -> "♔"; 'Q' -> "♕"; 'R' -> "♖"; 'B' -> "♗"; 'N' -> "♘"; 'P' -> "♙"
    'k' -> "♚"; 'q' -> "♛"; 'r' -> "♜"; 'b' -> "♝"; 'n' -> "♞"; 'p' -> "♟"
    else -> ""
} + if (p == ' ') "" else "\uFE0E" // erzwingt Text- statt Emoji-Darstellung

/**
 * Zeichnet ein Brett aus einer FEN. Gelb = letzter Zug, Blau = bester Zug laut Stockfish.
 */
@Composable
fun BoardView(
    fen: String,
    flipped: Boolean,
    lastMove: String?,
    bestMove: String?,
    modifier: Modifier = Modifier,
) {
    val grid = remember(fen) { parseFen(fen) }
    val last = remember(lastMove) { squaresOf(lastMove) }
    val best = remember(bestMove) { squaresOf(bestMove) }

    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(1f)) {
        val cell = this.maxWidth / 8
        val fontSize = with(LocalDensity.current) { (cell * 0.75f).toSp() }
        Column {
            for (r in 0 until 8) {
                Row {
                    for (c in 0 until 8) {
                        val rankIdx = if (flipped) 7 - r else r
                        val file = if (flipped) 7 - c else c
                        val name = "${'a' + file}${8 - rankIdx}"
                        val isLight = (file + rankIdx) % 2 == 0
                        val bg = when {
                            name in best -> BEST
                            name in last -> if (isLight) LAST_LIGHT else LAST_DARK
                            else -> if (isLight) LIGHT else DARK
                        }
                        Box(
                            Modifier.size(cell).background(bg),
                            contentAlignment = Alignment.Center,
                        ) {
                            val piece = grid[rankIdx][file]
                            if (piece != ' ') {
                                Text(glyph(piece), fontSize = fontSize, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
