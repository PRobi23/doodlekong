package doodlekong.robert

import doodlekong.robert.data.Player
import doodlekong.robert.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {

    val rooms = ConcurrentHashMap<String, Room>()
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    fun getRoomWithClientId(clientId: String): Room? {
        val filteredRooms = rooms.filterValues { room ->
            room.players.find { player ->
                player.clientId == clientId
            } != null
        }
        return if (filteredRooms.isEmpty()) {
            null
        } else {
            filteredRooms.values.first()
        }
    }

    fun playerLeft(clientId: String, immediatelyDisconnect: Boolean = false) {
        val playersRoom = getRoomWithClientId(clientId)
        if (immediatelyDisconnect) {
            println("Closing connection for players ${players[clientId]?.userName}")
            playersRoom?.removePlayer(clientId)
            players.remove(clientId)
        }
    }
}