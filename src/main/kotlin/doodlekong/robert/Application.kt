package doodlekong.robert

import doodlekong.robert.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val server = DrawingServer()

fun Application.module() {
    configureMonitoring()
    configureSockets()
    configureSerialization()
    configureRouting()
    configureSessions()
}
