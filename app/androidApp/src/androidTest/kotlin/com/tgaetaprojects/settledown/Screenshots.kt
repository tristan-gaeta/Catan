package com.tgaetaprojects.settledown

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.test.services.storage.TestStorage

/** Saves a capture through the test storage service; Gradle copies it to the build outputs after the run. */
fun SemanticsNodeInteraction.saveScreenshot(name: String) {
    val bitmap = captureToImage().asAndroidBitmap()
    TestStorage().openOutputFile("screenshots/$name.png").use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
