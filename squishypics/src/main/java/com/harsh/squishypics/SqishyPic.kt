package com.harsh.squishypics

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.harsh.squishypics.data.CompressQuality
import com.harsh.squishypics.data.CustomSettings
import java.util.concurrent.Executor

class SqishyPic {

    companion object {

        fun observeCompressor(
            context: Context,
            paths: List<String>,
            runnable: Runnable,
            executor: Executor
        ) {
            WorkManager.getInstance(context).getWorkInfos(WorkQuery.fromUniqueWorkNames(paths))
                .addListener(runnable, executor)
        }
    }

    // Allow to set custom settings in which the caller has to set target resolution and target quality
    // Allow to set High, Medium or Low which are nothing but predefined strategies
    // 0.75 (High) (0.5) Medium and (0.4) Low
    class Builder constructor(private val context: Context) {
        private var uri: Uri? = null
        private var fileName: String? = null
        private var tag: String? = null
        private var customSettings: CustomSettings? = null
        private var compressQuality: CompressQuality = CompressQuality.MEDIUM

        fun setUri(uri: Uri) = apply { this.uri = uri }

        // Unique tag used to get the compress status
        // If no value is passed, path will be used
        // If existing tag is used while another compressing is ongoing of same tag, nothing will happen
        fun setUniqueTag(tag: String) = apply { this.tag = tag }

        // File name of the image
        // If no name is passed, foll. file name will be generated in foll. format
        // IMG-yyyyMMdd-HHmmssSSS.jpg
        fun setFileName(fileName: String) = apply { this.fileName = fileName }

        fun setCompressQuality(quality: CompressQuality) = apply { this.compressQuality = quality }

        fun setCustomSettings(settings: CustomSettings) = apply { this.customSettings = settings }


        fun start() {
            if (uri == null) {
                throw IllegalArgumentException("Image uri must be set")
            }

            val targetQualityInPerc = customSettings?.targetQuality ?: compressQuality.targetQuality
            val targetWidth = customSettings?.targetWidth
            val targetHeight = customSettings?.targetHeight

            ImageCompressWorker.startWorker(
                context = context,
                targetQuality = targetQualityInPerc,
                targetHeight = targetHeight,
                targetWidth = targetWidth,
                uri = uri!!,
                uniqueTag = tag,
                fileName = fileName,
                compressQuality = compressQuality
            )
        }
    }
}