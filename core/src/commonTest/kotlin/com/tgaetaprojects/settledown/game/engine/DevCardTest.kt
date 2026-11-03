package com.tgaetaprojects.settledown.game.engine

import com.tgaetaprojects.settledown.game.DevelopmentCardType.KNIGHT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.MONOPOLY
import com.tgaetaprojects.settledown.game.DevelopmentCardType.ROAD_BUILDING
import com.tgaetaprojects.settledown.game.DevelopmentCardType.VICTORY_POINT
import com.tgaetaprojects.settledown.game.DevelopmentCardType.YEAR_OF_PLENTY
import com.tgaetaprojects.settledown.game.DiceTotal
import com.tgaetaprojects.settledown.game.Direction.NE
import com.tgaetaprojects.settledown.game.Hex
import com.tgaetaprojects.settledown.game.PlayerColor.BLUE
import com.tgaetaprojects.settledown.game.PlayerColor.RED
import com.tgaetaprojects.settledown.game.PlayerColor.WHITE
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import com.tgaetaprojects.settledown.game.engine.ResourceCounts.Companion.of
import com.tgaetaprojects.settledown.game.engine.RuleViolation.BANK_SHORTAGE
import com.tgaetaprojects.settledown.game.engine.RuleViolation.DECK_EMPTY
import com.tgaetaprojects.settledown.game.engine.RuleViolation.DEV_CARD_ALREADY_PLAYED
import com.tgaetaprojects.settledown.game.engine.RuleViolation.NO_PLAYABLE_CARD
import com.tgaetaprojects.settledown.game.engine.RuleViolation.WRONG_PHASE
import kotlin.test.Test
import kotlin.test.assertEquals

class DevCardTest {
    private val game = newGame()
    private val home = Hex.J.vertices.getValue(NE)
    private val emptyHex
        get() = Hex.entries.first {
            it != game.board.robber && game.robberVictims(
                it
            ).isEmpty()
        }

    @Test
    fun theDeckHasTheBaseGameMix() {
        assertEquals(25, game.devCardsRemaining)
        assertEquals(
            mapOf(
            KNIGHT to 14,
            VICTORY_POINT to 5,
            ROAD_BUILDING to 2,
            YEAR_OF_PLENTY to 2,
            MONOPOLY to 2
        ),
            game.devDeck.groupingBy { it }.eachCount()
        )
    }

    @Test
    fun cardsCannotBePlayedOnTheTurnTheyAreBought() {
        game.devDeck.remove(MONOPOLY)
        game.devDeck.addFirst(MONOPOLY)
        game.startTurnFor(RED)
        game.give(RED, ResourceCounts.DEV_CARD)

        game.apply(RED, Action.BuyDevCard).accepted()
        game.apply(RED, Action.PlayMonopoly(ORE)).rejectedWith(NO_PLAYABLE_CARD)
        game.apply(RED, Action.EndTurn).accepted()

        game.startTurnFor(RED)
        game.apply(RED, Action.PlayMonopoly(ORE)).accepted()
    }

    @Test
    fun buyingFailsOnceTheDeckIsEmpty() {
        game.devDeck.clear()
        game.startTurnFor(RED)
        game.give(RED, ResourceCounts.DEV_CARD)
        game.apply(RED, Action.BuyDevCard).rejectedWith(DECK_EMPTY)
    }

    @Test
    fun aKnightCanComeBeforeTheRollButOnlyOneCardPerTurn() {
        game.giveDevCard(RED, KNIGHT)
        game.giveDevCard(RED, KNIGHT)
        game.startTurnFor(RED, Phase.PreRoll)

        game.apply(RED, Action.PlayKnight).accepted()
        assertEquals(Phase.MoveRobber(returnTo = Phase.PreRoll), game.phase)
        game.apply(RED, Action.MoveRobber(emptyHex, null)).accepted()
        assertEquals(Phase.PreRoll, game.phase)

        game.apply(RED, Action.PlayKnight).rejectedWith(DEV_CARD_ALREADY_PLAYED)
        assertEquals(1, game.player(RED).knightsPlayed)
        assertEquals(1, game.player(RED).devCards[KNIGHT])
    }

    @Test
    fun roadBuildingPlacesTwoFreeRoads() {
        game.putSettlement(RED, home)
        game.giveDevCard(RED, ROAD_BUILDING)
        game.startTurnFor(RED)

        game.apply(RED, Action.PlayRoadBuilding).accepted()
        assertEquals(Phase.RoadBuilding(2, Phase.Main), game.phase)
        game.apply(RED, Action.BuildRoad(game.legalRoadSpots(RED).first()))
            .rejectedWith(WRONG_PHASE)
        repeat(2) { game.apply(RED, Action.PlaceRoad(game.legalRoadSpots(RED).first())).accepted() }

        assertEquals(Phase.Main, game.phase)
        assertEquals(PlayerState.ROADS - 2, game.player(RED).roadsLeft)
        assertEquals(ResourceCounts.FULL_BANK, game.bank)
    }

    @Test
    fun roadBuildingWithOneRoadLeftPlacesOne() {
        game.putSettlement(RED, home)
        game.giveDevCard(RED, ROAD_BUILDING)
        game.player(RED).roadsLeft = 1
        game.startTurnFor(RED)

        game.apply(RED, Action.PlayRoadBuilding).accepted()
        game.apply(RED, Action.PlaceRoad(game.legalRoadSpots(RED).first())).accepted()
        assertEquals(Phase.Main, game.phase)
    }

    @Test
    fun roadBuildingWithNoRoadsLeftOrNowhereToBuildIsSimplySpent() {
        game.putSettlement(RED, home)
        game.giveDevCard(RED, ROAD_BUILDING)
        game.giveDevCard(RED, KNIGHT)
        game.player(RED).roadsLeft = 0
        game.startTurnFor(RED)

        game.apply(RED, Action.PlayRoadBuilding).accepted()
        assertEquals(Phase.Main, game.phase)
        assertEquals(0, game.player(RED).devCards[ROAD_BUILDING])
        game.apply(RED, Action.PlayKnight).rejectedWith(DEV_CARD_ALREADY_PLAYED)

        // WHITE has roads to spare but no piece to build from
        game.giveDevCard(WHITE, ROAD_BUILDING)
        game.startTurnFor(WHITE)
        game.apply(WHITE, Action.PlayRoadBuilding).accepted()
        assertEquals(Phase.Main, game.phase)
    }

    @Test
    fun progressCardsCanAlsoBePlayedBeforeTheRoll() {
        game.putSettlement(RED, home)
        game.giveDevCard(RED, ROAD_BUILDING)
        game.giveDevCard(RED, YEAR_OF_PLENTY)
        game.giveDevCard(RED, MONOPOLY)
        game.forceRoll(DiceTotal.TWO)

        game.startTurnFor(RED, Phase.PreRoll)
        game.apply(RED, Action.PlayRoadBuilding).accepted()
        assertEquals(Phase.RoadBuilding(2, Phase.PreRoll), game.phase)
        repeat(2) { game.apply(RED, Action.PlaceRoad(game.legalRoadSpots(RED).first())).accepted() }
        assertEquals(Phase.PreRoll, game.phase)
        game.apply(RED, Action.RollDice).accepted()

        game.startTurnFor(RED, Phase.PreRoll)
        game.apply(RED, Action.PlayYearOfPlenty(ORE, ORE)).accepted()
        assertEquals(Phase.PreRoll, game.phase)

        game.give(WHITE, of(WOOL, 2))
        game.startTurnFor(RED, Phase.PreRoll)
        game.apply(RED, Action.PlayMonopoly(WOOL)).accepted()
        assertEquals(of(ORE to 2, WOOL to 2), game.player(RED).hand)
        assertEquals(Phase.PreRoll, game.phase)
    }

    @Test
    fun yearOfPlentyTakesTwoCardsTheBankCanSpare() {
        game.giveDevCard(RED, YEAR_OF_PLENTY)
        game.startTurnFor(RED)

        game.bank = game.bank.with(ORE, 1)
        game.apply(RED, Action.PlayYearOfPlenty(ORE, ORE)).rejectedWith(BANK_SHORTAGE)
        assertEquals(1, game.player(RED).devCards[YEAR_OF_PLENTY])

        game.apply(RED, Action.PlayYearOfPlenty(ORE, WOOL)).accepted()
        assertEquals(of(ORE, WOOL), game.player(RED).hand)
    }

    @Test
    fun monopolyTakesEveryCardOfOneTypeFromOpponents() {
        game.giveDevCard(RED, MONOPOLY)
        game.give(RED, of(ORE, 1))
        game.give(WHITE, of(ORE to 3, BRICK to 1))
        game.give(BLUE, of(ORE, 2))
        game.startTurnFor(RED)

        val events = game.apply(RED, Action.PlayMonopoly(ORE)).accepted()
        assertEquals(of(ORE, 6), game.player(RED).hand)
        assertEquals(of(BRICK, 1), game.player(WHITE).hand)
        assertEquals(GameEvent.Monopolized(RED, ORE, mapOf(WHITE to 3, BLUE to 2)), events.last())
    }

    @Test
    fun victoryPointCardsCountOnlyTowardTheHiddenTotal() {
        game.giveDevCard(RED, VICTORY_POINT)
        assertEquals(0, game.publicVictoryPoints(RED))
        assertEquals(1, game.totalVictoryPoints(RED))
    }
}
