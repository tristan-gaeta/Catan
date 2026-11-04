package com.tgaetaprojects.settledown.rooms

import com.tgaetaprojects.settledown.game.PlayerColor
import com.tgaetaprojects.settledown.store.GameStore
import java.security.SecureRandom

/** Creates a game for a full room: random seats and colors, and two seeds drawn independently. */
class GameStarter(private val games: GameStore) {
    private val random = SecureRandom()

    suspend fun start(members: List<Long>): Long {
        val seats = PlayerColor.entries.shuffled(random).zip(members.shuffled(random))
        return games.create(seats, boardSeed = random.nextLong(), gameSeed = random.nextLong())
    }
}
