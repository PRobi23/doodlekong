package doodlekong.robert.plugins

import createRoomRoute
import doodlekong.robert.routes.gameWebSocketRoute
import getRoomsRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*
import joinRoomRoute

fun Application.configureRouting() {
    routing {
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }
}
