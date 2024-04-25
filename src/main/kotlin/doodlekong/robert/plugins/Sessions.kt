package doodlekong.robert.plugins

import doodlekong.robert.session.DrawingSession
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.ktor.util.*

fun Application.configureSessions() {
    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }
    intercept(ApplicationCallPipeline.Plugins) {
        if (call.sessions.get<DrawingSession>() == null) {
            val clientId = call.parameters["clientId"] ?: ""
            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }
}