package com.tgaetaprojects.biophilia.game

import com.tgaetaprojects.biophilia.utils.enumSetOf
import com.tgaetaprojects.biophilia.utils.toEnumSet

enum class PortType(val exchangeRate: Int, val accepted: Set<Resource>) {
    BRICK(2, enumSetOf(Resource.BRICK)),
    LUMBER(2, enumSetOf(Resource.LUMBER)),
    ORE(2, enumSetOf(Resource.ORE)),
    GRAIN(2, enumSetOf(Resource.GRAIN)),
    WOOL(2, enumSetOf(Resource.WOOL)),
    GENERIC(3, Resource.entries.toEnumSet()),
}
