package doodlekong.robert.data

import io.ktor.websocket.*
import kotlinx.coroutines.isActive

data class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = emptyList(), // TODO Use copy
) {
    suspend fun broadcast(message: String) {
        players.forEach { player ->
            if (player.socket.isActive) {
                player.socket.send(
                    Frame.Text(message)
                )
            }
        }
    }

    suspend fun broadcastAllExcept(message: String, clientId: String) {
        players.forEach { player ->
            if (player.clientId != clientId && player.socket.isActive) {
                player.socket.send(
                    Frame.Text(message)
                )
            }
        }
    }
}