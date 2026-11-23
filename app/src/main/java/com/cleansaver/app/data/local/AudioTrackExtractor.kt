package com.cleansaver.app.data.local

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object AudioTrackExtractor {

    /**
     * Extracts the audio track from an MP4 file into a standalone M4A file using
     * Android's native MediaExtractor + MediaMuxer (zero native binary/FFmpeg overhead).
     */
    fun extractAudioToM4a(inputMp4: File, outputM4a: File): Boolean {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(inputMp4.absolutePath)
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }
            if (audioTrackIndex == -1 || audioFormat == null) return false

            extractor.selectTrack(audioTrackIndex)
            muxer = MediaMuxer(outputM4a.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(audioFormat)
            muxer.start()

            copyTrack(extractor, muxer, muxerTrackIndex, audioFormat)
            muxer.stop()
            outputM4a.exists() && outputM4a.length() > 0
        } catch (_: Exception) {
            false
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    /**
     * Muxes a separate DASH video MP4 and DASH audio MP4 (e.g., Reddit videos) into a single
     * playable MP4 file using Android's native MediaExtractor + MediaMuxer.
     */
    fun muxVideoAndAudio(videoFile: File, audioFile: File, outputMp4: File): Boolean {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            var videoTrack = -1
            var videoFormat: MediaFormat? = null
            for (i in 0 until videoExtractor.trackCount) {
                val f = videoExtractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("video/")) {
                    videoTrack = i
                    videoFormat = f
                    break
                }
            }

            var audioTrack = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until audioExtractor.trackCount) {
                val f = audioExtractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrack = i
                    audioFormat = f
                    break
                }
            }

            if (videoTrack == -1 || videoFormat == null || audioTrack == -1 || audioFormat == null) {
                return false
            }

            videoExtractor.selectTrack(videoTrack)
            audioExtractor.selectTrack(audioTrack)

            muxer = MediaMuxer(outputMp4.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outVideoTrack = muxer.addTrack(videoFormat)
            val outAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            copyTrack(videoExtractor, muxer, outVideoTrack, videoFormat)
            copyTrack(audioExtractor, muxer, outAudioTrack, audioFormat)

            muxer.stop()
            outputMp4.exists() && outputMp4.length() > 0
        } catch (_: Exception) {
            false
        } finally {
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor.release() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun copyTrack(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        format: MediaFormat
    ) {
        val bufferSize = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
            format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(65536)
        } else {
            256 * 1024
        }
        val byteBuffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(byteBuffer, 0)
            if (bufferInfo.size < 0) {
                bufferInfo.size = 0
                break
            }
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags
            muxer.writeSampleData(muxerTrackIndex, byteBuffer, bufferInfo)
            extractor.advance()
        }
    }
}
