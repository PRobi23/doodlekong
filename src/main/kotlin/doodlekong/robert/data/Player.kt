package doodlekong.robert.data

import io.ktor.websocket.*

data class Player(
    val userName: String,
    var socket: WebSocketSession, //TODO THIS can be val and use copy
    val clientId: String,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    val rank: Int = 0
)
