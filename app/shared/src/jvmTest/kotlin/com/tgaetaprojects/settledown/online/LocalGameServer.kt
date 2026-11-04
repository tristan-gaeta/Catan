package com.tgaetaprojects.settledown.online

import com.tgaetaprojects.settledown.Services
import com.tgaetaprojects.settledown.auth.DevTokenVerifier
import com.tgaetaprojects.settledown.module
import com.tgaetaprojects.settledown.store.Database
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Random
import kotlin.io.path.deleteIfExists

/** The real server on a free local port, with dev sign-in and a temporary database. */
internal class LocalGameServer {
    private val databasePath = Files.createTempFile("settledown-e2e", ".db")
    private val server = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
        module(
            Services(Database(databasePath.toString()), DevTokenVerifier, random = Random(7), maxActionsPerSecond = 10_000)
        )
    }.also { it.start(wait = false) }

    val port: Int = runBlocking { server.engine.resolvedConnectors().single().port }

    /** Safe to call twice, for a test that stops it early to see how the client copes. */
    fun stop() {
        server.stop(100, 1000)
        databasePath.deleteIfExists()
        databasePath.resolveSibling("${databasePath.fileName}-wal").deleteIfExists()
        databasePath.resolveSibling("${databasePath.fileName}-shm").deleteIfExists()
    }
}
