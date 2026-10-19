package de.mm20.launcher2.ui.camera

/** Power-of-two decoder sampling keeps conversion allocations bounded even for oversized OEM JPEGs. */
internal object CameraImageSizing {
    fun sampleSize(width: Int, height: Int, maxPixels: Long): Int {
        require(width in 1..100_000 && height in 1..100_000 && maxPixels > 0)
        var sample = 1
        while (((width.toLong() + sample - 1) / sample) * ((height.toLong() + sample - 1) / sample) > maxPixels) {
            sample *= 2
        }
        return sample
    }
}
