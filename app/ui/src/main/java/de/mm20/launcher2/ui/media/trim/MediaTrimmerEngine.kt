package de.mm20.launcher2.ui.media.trim

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

object MediaTrimmerEngine {

    /**
     * Losslessly trims an audio or video file between [startMs] and [endMs].
     * Supports muting video ([removeAudio]).
     */
    suspend fun trimMedia(
        context: Context,
        inputUri: Uri,
        startMs: Long,
        endMs: Long,
        outputFile: File,
        removeAudio: Boolean = false,
    ): File? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, inputUri, null)

            val trackCount = extractor.trackCount
            muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val trackIndexMap = HashMap<Int, Int>()
            var bufferSize = 1024 * 1024

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue

                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i)
                    val newTrackIndex = muxer.addTrack(format)
                    trackIndexMap[i] = newTrackIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (newSize > bufferSize) bufferSize = newSize
                    }
                } else if (mime.startsWith("audio/") && !removeAudio) {
                    extractor.selectTrack(i)
                    val newTrackIndex = muxer.addTrack(format)
                    trackIndexMap[i] = newTrackIndex
                    if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                        val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        if (newSize > bufferSize) bufferSize = newSize
                    }
                }
            }

            if (trackIndexMap.isEmpty()) return@withContext null

            muxer.start()

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endUs) break

                if (presentationTimeUs >= startUs) {
                    bufferInfo.presentationTimeUs = presentationTimeUs - startUs
                    val sampleFlags = extractor.sampleFlags
                    bufferInfo.flags = if ((sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else {
                        0
                    }
                    val trackIndex = extractor.sampleTrackIndex
                    val muxerTrackIndex = trackIndexMap[trackIndex]
                    if (muxerTrackIndex != null) {
                        muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                    }
                }
                extractor.advance()
            }

            outputFile
        } catch (e: Exception) {
            android.util.Log.e("MediaTrimmerEngine", "Trimming failed", e)
            null
        } finally {
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor?.release() }
        }
    }

    /**
     * Extracts the audio track from a video file into an M4A/AAC audio file.
     */
    suspend fun extractAudio(
        context: Context,
        inputUri: Uri,
        outputFile: File,
    ): File? = withContext(Dispatchers.IO) {
        var extractor: MediaExtractor? = null
        var muxer: MediaMuxer? = null
        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, inputUri, null)

            val trackCount = extractor.trackCount
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) return@withContext null

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            var bufferSize = 1024 * 1024
            if (audioFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                val newSize = audioFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                if (newSize > bufferSize) bufferSize = newSize
            }

            val buffer = ByteBuffer.allocateDirect(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(muxerAudioTrack, buffer, bufferInfo)
                extractor.advance()
            }

            outputFile
        } catch (e: Exception) {
            android.util.Log.e("MediaTrimmerEngine", "Audio extraction failed", e)
            null
        } finally {
            runCatching { muxer?.stop(); muxer?.release() }
            runCatching { extractor?.release() }
        }
    }
}
