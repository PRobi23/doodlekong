package doodlekong.robert.plugins

import createRoomRoute
import getRoomsRoute
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        createRoomRoute()
        getRoomsRoute()
    }
}
