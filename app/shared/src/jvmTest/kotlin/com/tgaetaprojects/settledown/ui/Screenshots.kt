package com.tgaetaprojects.settledown.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File

/** Saves UI test captures as PNGs under the directory Gradle passes in `screenshotDir`, for review and CI. */
internal object Screenshots {
    private val dir = File(System.getProperty("screenshotDir") ?: "build/screenshots")

    fun save(image: ImageBitmap, name: String) {
        dir.mkdirs()
        val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
            ?: error("Couldn't encode $name")
        File(dir, "$name.png").writeBytes(png.bytes)
    }
}
