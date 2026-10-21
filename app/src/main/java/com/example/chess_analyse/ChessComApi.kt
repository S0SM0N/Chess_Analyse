package com.example.chess_analyse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object ChessComApi {
    private val client = OkHttpClient()
    private const val BASE = "https://api.chess.com/pub/"
    // chess.com bittet darum, einen aussagekräftigen User-Agent zu senden
    private const val USER_AGENT = "ChessAnalyzer/1.0 (privates Hobbyprojekt)"

    private fun get(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) error("Benutzer oder Daten nicht gefunden (404)")
            if (!response.isSuccessful) error("chess.com-Fehler: HTTP ${response.code}")
            return response.body?.string() ?: error("Leere Antwort von chess.com")
        }
    }

    /** Liste aller Monatsarchive (älteste zuerst). */
    suspend fun archives(username: String): List<String> = withContext(Dispatchers.IO) {
        val json = JSONObject(get("${BASE}player/${username.trim().lowercase()}/games/archives"))
        val arr = json.getJSONArray("archives")
        List(arr.length()) { arr.getString(it) }
    }

    /** Alle Standard-Schachpartien eines Monatsarchivs, neueste zuerst. */
    suspend fun gamesOfArchive(archiveUrl: String): List<ChessGame> = withContext(Dispatchers.IO) {
        val json = JSONObject(get(archiveUrl))
        val arr = json.getJSONArray("games")
        val result = mutableListOf<ChessGame>()
        for (i in 0 until arr.length()) {
            val g = arr.getJSONObject(i)
            if (!g.has("pgn")) continue
            if (g.optString("rules", "chess") != "chess") continue // Varianten haben andere Startstellungen
            result += ChessGame(
                url = g.getString("url"),
                pgn = g.getString("pgn"),
                timeControl = g.optString("time_control"),
                timeClass = g.optString("time_class"),
                endTime = g.optLong("end_time"),
                white = parsePlayer(g.getJSONObject("white")),
                black = parsePlayer(g.getJSONObject("black")),
            )
        }
        result.sortedByDescending { it.endTime }
    }

    private fun parsePlayer(o: JSONObject) =
        Player(o.optString("username"), o.optInt("rating"), o.optString("result"))
}
