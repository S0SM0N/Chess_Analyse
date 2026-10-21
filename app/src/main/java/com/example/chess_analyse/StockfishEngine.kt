package com.example.chess_analyse

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Nutzt vorrangig eine native Stockfish-Binary (app/src/main/jniLibs/<abi>/libstockfish.so).
 * Falls keine native Binary installiert ist, wird automatisch eine Online-Stockfish-API als Fallback verwendet.
 */
class StockfishEngine(private val context: Context) {
    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null
    private val mutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun analyze(fen: String, moveTimeMs: Int = 800): Analysis = mutex.withLock {
        val nativeFile = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (nativeFile.exists()) {
            runCatching {
                analyzeNative(fen, moveTimeMs)
            }.getOrElse {
                analyzeOnline(fen)
            }
        } else {
            analyzeOnline(fen)
        }
    }

    private suspend fun analyzeNative(fen: String, moveTimeMs: Int): Analysis = withContext(Dispatchers.IO) {
        ensureStarted()
        send("position fen $fen")
        send("go movetime $moveTimeMs")

        var cp: Int? = null
        var mate: Int? = null
        var depth = 0
        var best = ""
        while (true) {
            val line = readLine()
            if (line.startsWith("info ") && " score " in line &&
                "lowerbound" !in line && "upperbound" !in line
            ) {
                Regex("depth (\\d+)").find(line)?.let { depth = it.groupValues[1].toInt() }
                val m = Regex("score (cp|mate) (-?\\d+)").find(line)
                if (m != null) {
                    val v = m.groupValues[2].toInt()
                    if (m.groupValues[1] == "cp") { cp = v; mate = null } else { mate = v; cp = null }
                }
            } else if (line.startsWith("bestmove")) {
                best = line.split(" ").getOrElse(1) { "" }.let { if (it == "(none)") "" else it }
                break
            }
        }

        // Stockfish bewertet aus Sicht des Spielers am Zug -> auf Weiß umrechnen
        val whiteToMove = fen.split(" ").getOrElse(1) { "w" } == "w"
        val sign = if (whiteToMove) 1 else -1
        Analysis(
            bestMoveUci = best,
            bestMoveSan = if (best.isNotEmpty()) PgnParser.uciToSan(fen, best) else null,
            evalCp = cp?.let { it * sign },
            mate = mate?.let { it * sign },
            depth = depth,
        )
    }

    private suspend fun analyzeOnline(fen: String): Analysis = withContext(Dispatchers.IO) {
        val encodedFen = URLEncoder.encode(fen, "UTF-8")

        // 1. Versuch: stockfish.online API
        runCatching {
            val url = "https://stockfish.online/api/s/v2.php?fen=$encodedFen&depth=12"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ChessAnalyzer/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success")) {
                            val bestStr = json.optString("bestmove", "")
                            val bestMove = bestStr.split(" ").getOrElse(1) { "" }.let { if (it == "(none)") "" else it }
                            val evalVal = if (!json.isNull("evaluation")) json.optDouble("evaluation") else null
                            val cp = evalVal?.let { (it * 100).toInt() }
                            val mate = if (!json.isNull("mate")) json.optInt("mate") else null
                            val depth = json.optInt("depth", 12)

                            return@withContext Analysis(
                                bestMoveUci = bestMove,
                                bestMoveSan = if (bestMove.isNotEmpty()) PgnParser.uciToSan(fen, bestMove) else null,
                                evalCp = cp,
                                mate = mate,
                                depth = depth,
                            )
                        }
                    }
                }
            }
        }

        // 2. Fallback: Lichess Cloud Evaluation API
        runCatching {
            val url = "https://lichess.org/api/cloud-eval?fen=$encodedFen"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ChessAnalyzer/1.0")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        val pvs = json.optJSONArray("pvs")
                        if (pvs != null && pvs.length() > 0) {
                            val pv0 = pvs.getJSONObject(0)
                            val movesStr = pv0.optString("moves", "")
                            val bestMove = movesStr.split(" ").firstOrNull() ?: ""
                            val cp = if (pv0.has("cp")) pv0.optInt("cp") else null
                            val mate = if (pv0.has("mate")) pv0.optInt("mate") else null
                            val depth = json.optInt("depth", 12)

                            return@withContext Analysis(
                                bestMoveUci = bestMove,
                                bestMoveSan = if (bestMove.isNotEmpty()) PgnParser.uciToSan(fen, bestMove) else null,
                                evalCp = cp,
                                mate = mate,
                                depth = depth,
                            )
                        }
                    }
                }
            }
        }

        error("Analyse fehlgeschlagen (keine lokale Stockfish-Binary vorhanden und Online-API nicht erreichbar).")
    }

    private fun ensureStarted() {
        if (process != null) return
        val file = File(context.applicationInfo.nativeLibraryDir, "libstockfish.so")
        if (!file.exists()) {
            error("Stockfish-Binary fehlt (${file.absolutePath}).")
        }
        val p = ProcessBuilder(file.absolutePath).redirectErrorStream(true).start()
        process = p
        writer = p.outputStream.bufferedWriter()
        reader = p.inputStream.bufferedReader()
        send("uci"); waitFor("uciok")
        send("setoption name Threads value 2")
        send("setoption name Hash value 64")
        send("isready"); waitFor("readyok")
    }

    private fun send(cmd: String) {
        writer!!.write(cmd + "\n")
        writer!!.flush()
    }

    private fun readLine(): String = reader!!.readLine() ?: error("Stockfish wurde unerwartet beendet")

    private fun waitFor(prefix: String) {
        while (true) if (readLine().startsWith(prefix)) return
    }

    fun close() {
        runCatching { send("quit") }
        process?.destroy()
        process = null
    }
}