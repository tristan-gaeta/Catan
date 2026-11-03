package com.tgaetaprojects.settledown.game

enum class Terrain(val resource: Resource?) {
    HILL(Resource.BRICK),
    FOREST(Resource.LUMBER),
    MOUNTAIN(Resource.ORE),
    FIELDS(Resource.GRAIN),
    PASTURE(Resource.WOOL),
    DESERT(null);
}
