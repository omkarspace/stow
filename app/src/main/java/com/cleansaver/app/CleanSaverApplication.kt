package com.cleansaver.app

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.cleansaver.app.di.AppContainer
import com.cleansaver.app.util.ExtractionLogger
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CleanSaverApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Initialize universal client-side yt-dlp + FFmpeg + Aria2c engine asynchronously.
        // Catch Throwable (including UnsatisfiedLinkError / NoClassDefFoundError) so init can never crash launch.
        container.applicationScope.launch(Dispatchers.IO) {
            try {
                YoutubeDL.getInstance().init(this@CleanSaverApplication)
                ExtractionLogger.log("YT-DLP", "YoutubeDL initialized")
            } catch (t: Throwable) {
                ExtractionLogger.log("YT-DLP", "YoutubeDL init error: ${t.javaClass.simpleName}: ${t.message}")
            }
            try {
                FFmpeg.getInstance().init(this@CleanSaverApplication)
                ExtractionLogger.log("YT-DLP", "FFmpeg initialized")
            } catch (t: Throwable) {
                ExtractionLogger.log("YT-DLP", "FFmpeg init error: ${t.javaClass.simpleName}: ${t.message}")
            }
            try {
                Aria2c.getInstance().init(this@CleanSaverApplication)
                ExtractionLogger.log("YT-DLP", "Aria2c initialized")
            } catch (t: Throwable) {
                ExtractionLogger.log("YT-DLP", "Aria2c init error: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(container.workerFactory)
            .build()

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
