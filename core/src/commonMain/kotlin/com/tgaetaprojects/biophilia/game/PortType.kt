package com.tgaetaprojects.biophilia.game

enum class PortType(val exchangeRate: Int, val accepted: Resource?) {
    BRICK(2, Resource.BRICK),
    LUMBER(2, Resource.LUMBER),
    ORE(2, Resource.ORE),
    GRAIN(2, Resource.GRAIN),
    WOOL(2, Resource.WOOL),
    GENERIC(3, null),
}
