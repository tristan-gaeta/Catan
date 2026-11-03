package com.tgaetaprojects.settledown

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform