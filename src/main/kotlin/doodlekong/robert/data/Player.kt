package doodlekong.robert.data

import doodlekong.robert.data.models.Ping
import doodlekong.robert.gson
import doodlekong.robert.server
import doodlekong.robert.utility.Constants.PING_FREQUENCY
import io.ktor.websocket.*
import kotlinx.coroutines.*

data class Player(
    val userName: String,
    var socket: WebSocketSession, //TODO THIS can be val and use copy
    val clientId: String,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    val rank: Int = 0
) {
    private var pingJob: Job? = null

    private var pingTime = 0L
    private var pongTime = 0L

    var isOnline = true

    @OptIn(DelicateCoroutinesApi::class)
    fun startPinging() {
        pingJob?.cancel()
        pingJob = GlobalScope.launch {
            while (true) {
                sendPing()
                delay(PING_FREQUENCY)
            }
        }
    }

    private suspend fun sendPing() {
        pingTime = System.currentTimeMillis()
        socket.send(Frame.Text(gson.toJson(Ping())))
        delay(PING_FREQUENCY)
        if (pingTime - pongTime > PING_FREQUENCY) {
            isOnline = false
            server.playerLeft(clientId)
            pingJob?.cancel()
        }
    }

    fun receivedPong() {
        pongTime = System.currentTimeMillis()
        isOnline = true
    }

    fun disconnect() {
        pingJob?.cancel()
    }
}
