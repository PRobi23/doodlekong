package doodlekong.robert

import com.google.gson.Gson
import doodlekong.robert.plugins.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

val server = DrawingServer()
val gson = Gson()

fun Application.module() {
    configureMonitoring()
    configureSockets()
    configureSerialization()
    configureRouting()
    configureSessions()
}
