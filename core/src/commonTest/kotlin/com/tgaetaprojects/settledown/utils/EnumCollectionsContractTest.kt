package com.tgaetaprojects.settledown.utils

import com.tgaetaprojects.settledown.game.Edge
import com.tgaetaprojects.settledown.game.Resource
import com.tgaetaprojects.settledown.game.Resource.BRICK
import com.tgaetaprojects.settledown.game.Resource.GRAIN
import com.tgaetaprojects.settledown.game.Resource.ORE
import com.tgaetaprojects.settledown.game.Resource.WOOL
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnumCollectionsContractTest {

    private fun <T> assertEqualBothWays(a: T, b: T) {
        assertEquals(a, b)
        assertEquals(b, a)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ---- Set contract ----

    @Test
    fun setEqualityIsSymmetricWithOtherSets() {
        val set = enumSetOf(ORE, BRICK)
        assertEqualBothWays<Set<Resource>>(set, setOf(BRICK, ORE))
        assertEqualBothWays<Set<Resource>>(set, hashSetOf(ORE, BRICK))
        assertEqualBothWays<Set<Resource>>(set, mutableEnumSetOf(BRICK, ORE))
        assertEqualBothWays<Set<Resource>>(enumSetOf(), emptySet())

        assertNotEquals<Set<Resource>>(set, setOf(BRICK))
        assertNotEquals<Set<Resource>>(setOf(BRICK, GRAIN), set)
        assertNotEquals<Any>(set, listOf(BRICK, ORE))
    }

    @Test
    fun largeEnumSetsHonourTheContract() {
        // More than 64 constants, so java.util.EnumSet switches to its long[] implementation
        val edges = Edge.entries.filter { it.ordinal % 3 == 0 }
        assertEqualBothWays<Set<Edge>>(edges.toEnumSet(), edges.toSet())
        assertEqualBothWays<Set<Edge>>(enumSetAllOf<Edge>(), Edge.entries.toSet())
    }

    @Test
    fun setIteratesInOrdinalOrderAndPrintsLikeKotlin() {
        val set = mutableEnumSetOf(WOOL, BRICK, GRAIN)
        assertEquals(listOf(BRICK, GRAIN, WOOL), set.toList())
        assertEquals(setOf(BRICK, GRAIN, WOOL).toString(), set.toString())
    }

    @Test
    fun emptySetCopiesDoNotThrow() {
        assertTrue(emptyList<Resource>().toEnumSet().isEmpty())
        assertTrue(enumSetOf(emptySet<Resource>()).isEmpty())
        assertTrue(enumSetOf<Resource>().toEnumSet().isEmpty())
        assertTrue(emptySequence<Resource>().toMutableEnumSet().isEmpty())
        assertTrue(emptyArray<Resource>().toEnumSet().isEmpty())
    }

    @Test
    fun setCopiesAreIndependent() {
        val source = mutableEnumSetOf(BRICK)
        val copy = source.toMutableEnumSet()
        copy += ORE
        source -= BRICK
        assertEquals(emptySet(), source)
        assertEquals(setOf(BRICK, ORE), copy)
    }

    @Test
    fun setMutationThroughIteratorAndWrongTypeLookups() {
        val set = mutableEnumSetOf(BRICK, ORE, WOOL)
        val it = set.iterator()
        while (it.hasNext()) if (it.next() == ORE) it.remove()
        assertEquals(setOf(BRICK, WOOL), set)

        @Suppress("UNCHECKED_CAST")
        val untyped = set as MutableSet<Any>
        assertFalse("BRICK" in untyped)
        assertFalse(untyped.remove("BRICK"))
    }

    // ---- Map contract ----

    @Test
    fun mapEqualityIsSymmetricWithOtherMaps() {
        val map = enumMapOf(ORE to 2, BRICK to 1)
        assertEqualBothWays<Map<Resource, Int>>(map, mapOf(BRICK to 1, ORE to 2))
        assertEqualBothWays<Map<Resource, Int>>(map, hashMapOf(ORE to 2, BRICK to 1))
        assertEqualBothWays<Map<Resource, Int>>(map, mutableEnumMapOf(BRICK to 1, ORE to 2))
        assertEqualBothWays<Map<Resource, Int>>(enumMapOf(), emptyMap())

        assertNotEquals<Map<Resource, Int>>(map, mapOf(BRICK to 1, ORE to 3))
        assertNotEquals<Map<Resource, Int>>(mapOf(BRICK to 1), map)
        assertEquals(mapOf(BRICK to 1, ORE to 2).entries, map.entries)
    }

    @Test
    fun mapIteratesInOrdinalOrderAndPrintsLikeKotlin() {
        val map = mutableEnumMapOf(WOOL to 3, BRICK to 1, GRAIN to 2)
        assertEquals(listOf(BRICK, GRAIN, WOOL), map.keys.toList())
        assertEquals(listOf(1, 2, 3), map.values.toList())
        assertEquals(mapOf(BRICK to 1, GRAIN to 2, WOOL to 3).toString(), map.toString())
    }

    @Test
    fun emptyMapCopiesDoNotThrow() {
        assertTrue(emptyMap<Resource, Int>().toEnumMap().isEmpty())
        assertTrue(mutableEnumMapOf(emptyMap<Resource, Int>()).isEmpty())
        assertTrue(enumMapOf<Resource, Int>().toMutableEnumMap().isEmpty())
        assertTrue(EnumMap(emptyMap<Resource, Int>()).isEmpty())
        assertTrue(emptyList<Pair<Resource, Int>>().toEnumMap().isEmpty())
    }

    @Test
    fun mapCopiesAreIndependent() {
        val source = mutableEnumMapOf(BRICK to 1)
        val copy = source.toMutableEnumMap()
        copy[ORE] = 2
        source[BRICK] = 9
        assertEquals(mapOf(BRICK to 9), source)
        assertEquals(mapOf(BRICK to 1, ORE to 2), copy)
    }

    @Test
    fun mapViewsWriteThrough() {
        val map = mutableEnumMapOf(BRICK to 1, ORE to 2, WOOL to 3, GRAIN to 4)
        map.keys.remove(BRICK)
        map.values.remove(2)
        map.entries.removeAll { it.key == WOOL }
        map.entries.single().setValue(40)
        assertEquals(mapOf(GRAIN to 40), map)
    }

    @Test
    fun mapAllowsNullValuesAndToleratesWrongTypeLookups() {
        val map = mutableEnumMapOf<Resource, Int?>(BRICK to null)
        assertTrue(map.containsKey(BRICK))
        assertFalse(map.containsKey(ORE))
        assertNull(map[BRICK])
        assertEquals(mapOf<Resource, Int?>(BRICK to null), map)

        @Suppress("UNCHECKED_CAST")
        val untyped = map as Map<Any, Any?>
        assertNull(untyped["BRICK"])
        assertFalse(untyped.containsKey("BRICK"))
    }

    // ---- Serialization ----

    @Serializable
    private data class Holder(val stock: EnumMap<Resource, Int>)

    @Test
    fun enumMapSerializesLikeARegularMap() {
        val serializer = EnumMapSerializer(serializer<Resource>(), Int.serializer())
        val map = enumMapOf(ORE to 2, BRICK to 1)

        val json = Json.encodeToString(serializer, map)
        assertEquals(Json.encodeToString(mapOf(BRICK to 1, ORE to 2)), json)
        assertEquals(map, Json.decodeFromString(serializer, json))
        assertEquals(enumMapOf(), Json.decodeFromString(serializer, "{}"))
    }

    @Test
    fun enumMapPropertiesPickUpTheSerializer() {
        val holder = Holder(enumMapOf(WOOL to 4))
        val decoded = Json.decodeFromString<Holder>(Json.encodeToString(holder))
        assertEquals(holder, decoded)
        assertTrue(decoded.stock is MutableEnumMap<*, *>)
    }

    @Test
    fun byteArraysRoundTripAsBase64() {
        val bytes = byteArrayOf(0, 1, 127, -128, -1)
        val json = Json.encodeToString(Base64ByteArraySerializer, bytes)
        assertEquals("\"AAF/gP8=\"", json)
        assertTrue(bytes.contentEquals(Json.decodeFromString(Base64ByteArraySerializer, json)))
    }
}
