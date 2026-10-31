package de.mm20.launcher2.wallpapers

/** Bounded, separable box blur. Work and memory are independent of the source photo size. */
internal object GlassPixels {
    const val MAX_EDGE = 512
    /** Extra downsample before CPU/GPU blur (~1/8 linear, ~98% fewer pixels). */
    const val BLUR_SAMPLE = 8
    fun sampleSize(width: Int, height: Int): Int {
        require(width > 0 && height > 0 && width.toLong() * height <= 100_000_000L)
        var sample = 1
        while ((maxOf(width, height) + sample - 1) / sample > MAX_EDGE) sample *= 2
        return sample
    }
    fun blurSampleSize(width: Int, height: Int): Int = maxOf(sampleSize(width, height), BLUR_SAMPLE)
    fun sourceRadius(radiusDp: Int, imageWidth: Int, imageHeight: Int, windowWidthDp: Int, windowHeightDp: Int): Int {
        require(minOf(imageWidth, imageHeight, windowWidthDp, windowHeightDp) > 0)
        if (radiusDp <= 0) return 0
        val scale = maxOf(windowWidthDp.toFloat() / imageWidth, windowHeightDp.toFloat() / imageHeight)
        return kotlin.math.round(radiusDp.coerceIn(0, 100) / scale).toInt().coerceIn(1, 32)
    }
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        require(width in 1..MAX_EDGE && height in 1..MAX_EDGE && pixels.size == width * height)
        require(radius in 0..32)
        if (radius == 0) return pixels.copyOf()
        var source = pixels.copyOf()
        var target = IntArray(source.size)
        repeat(3) {
            for (vertical in listOf(false, true)) {
                val lines = if (vertical) width else height
                val length = if (vertical) height else width
                val count = radius * 2 + 1
                for (line in 0 until lines) {
                    fun index(position: Int) = if (vertical) position.coerceIn(0, length - 1) * width + line
                        else line * width + position.coerceIn(0, length - 1)
                    var a = 0; var r = 0; var g = 0; var b = 0
                    fun add(pixel: Int, sign: Int) {
                        a += (pixel ushr 24) * sign; r += ((pixel ushr 16) and 255) * sign
                        g += ((pixel ushr 8) and 255) * sign; b += (pixel and 255) * sign
                    }
                    for (offset in -radius..radius) add(source[index(offset)], 1)
                    for (position in 0 until length) {
                        target[index(position)] = ((a / count) shl 24) or ((r / count) shl 16) or
                            ((g / count) shl 8) or (b / count)
                        add(source[index(position - radius)], -1)
                        add(source[index(position + radius + 1)], 1)
                    }
                }
                val previous = source; source = target; target = previous
            }
        }
        return source
    }
}
