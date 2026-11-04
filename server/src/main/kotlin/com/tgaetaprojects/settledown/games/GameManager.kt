package com.tgaetaprojects.settledown.games

import com.tgaetaprojects.settledown.api.GameStatus
import com.tgaetaprojects.settledown.game.codec.GameRecorder
import com.tgaetaprojects.settledown.game.codec.ServerMessage
import com.tgaetaprojects.settledown.game.engine.viewFor
import com.tgaetaprojects.settledown.store.GameStore
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** The games being played right now. There's one [GameHost] per active game. */
class GameManager(
    private val store: GameStore,
    private val limits: TurnLimits = TurnLimits(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val hosts = ConcurrentHashMap<Long, GameHost>()
    private lateinit var scope: CoroutineScope
    private lateinit var scheduler: Scheduler

    /** Sets where hosts run. Must be called before anything else. */
    fun attach(scope: CoroutineScope, scheduler: Scheduler = GameHost.scopeScheduler(scope)) {
        this.scope = scope
        this.scheduler = scheduler
    }

    /**
     * Rebuilds every active game from its saved actions, after a restart. A game saved by another version of the
     * rules or formats can't be replayed, so it's marked unavailable rather than left to fail on every start.
     */
    suspend fun resumeAll() {
        for (gameId in store.activeGameIds()) {
            try {
                startHost(gameId)
            } catch (e: IllegalArgumentException) { // including CodecException
                markUnavailable(gameId, e)
            } catch (e: IllegalStateException) {
                markUnavailable(gameId, e)
            }
        }
    }

    /** Starts playing a game that was just created in the store. */
    suspend fun startHost(gameId: Long) {
        val recorder = GameRecorder.resume(store.loadRecord(gameId))
        val host = GameHost(gameId, recorder, store.seats(gameId), store, scheduler, limits, now) { hosts.remove(it.gameId) }
        hosts[gameId] = host
        host.start(scope)
    }

    fun host(gameId: Long): GameHost? = hosts[gameId]

    suspend fun removeAccount(accountId: Long) {
        hosts.values.forEach { it.removeAccount(accountId) }
    }

    suspend fun disconnectAccount(accountId: Long) {
        hosts.values.forEach { it.disconnectAccount(accountId) }
    }

    /** Whether [accountId] holds a seat at a game that isn't running now but will resume when the server restarts. */
    suspend fun isWaitingToResume(gameId: Long, accountId: Long): Boolean =
        !hosts.containsKey(gameId) && store.status(gameId) == GameStatus.ACTIVE &&
                store.seats(gameId).any { it.accountId == accountId }

    /**
     * The final state of a finished game for a player who wasn't connected when it ended, so they can still see who
     * won. Returns `null` if the game isn't finished, or [accountId] had no seat at it.
     */
    suspend fun finalUpdate(gameId: Long, accountId: Long): ServerMessage.Update? {
        if (store.status(gameId) != GameStatus.FINISHED) return null
        val seat = store.seats(gameId).firstOrNull { it.accountId == accountId } ?: return null
        return try {
            ServerMessage.Update(store.loadRecord(gameId).replay().viewFor(seat.color), emptyList(), 0)
        } catch (_: IllegalArgumentException) { // including CodecException
            null
        } catch (_: IllegalStateException) {
            null
        }
    }

    private suspend fun markUnavailable(gameId: Long, cause: Exception) {
        log.warn("Game $gameId can't be resumed on this version: ${cause.message}")
        store.finish(gameId, GameStatus.UNAVAILABLE)
    }

    private companion object {
        val log = LoggerFactory.getLogger(GameManager::class.java)
    }
}
