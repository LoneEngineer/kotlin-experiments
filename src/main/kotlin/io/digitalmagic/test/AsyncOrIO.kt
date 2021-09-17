package io.digitalmagic.test

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.Authentication.Feature.install
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.sessions.*
import java.io.File

fun main() { // example from https://youtrack.jetbrains.com/issue/KTOR-1452  - works fine in current setup (kotlin 1.4.31, ktor 1.5.2, jackson 2.12.5)
    embeddedServer(Netty, port = 8081) {
        install(Sessions) {
            cookie<UserIdPrincipal>(
                "SESSION_ID",
                storage = directorySessionStorage(File(".sessions"), cached = true)
            ) {
                cookie.path = "/"
                cookie.httpOnly = true
                cookie.extensions["SameSite"] = "Lax"
            }
        }
        routing {
            get("/") {
                call.respondText { "Hello world" }
            }
            get("/auth") {
                call.sessions.set(UserIdPrincipal("me"))
                call.respondText { "Success" }
            }
        }
    }.start(wait = true)
}
