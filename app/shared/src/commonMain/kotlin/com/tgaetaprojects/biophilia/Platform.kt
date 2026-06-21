package com.tgaetaprojects.biophilia

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform