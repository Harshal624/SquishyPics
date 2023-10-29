package com.harsh.squishypics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.harsh.squishypics.data.CompressQuality
import com.harsh.squishypics.utils.FileUtils


internal class ImageCompressWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {

        private const val KEY_FILE_PATH = "key_file_path"
        private const val KEY_TARGET_QUALITY = "key_target_quality"
        private const val KEY_TARGET_RES_WIDTH = "key_target_res_width"
        private const val KEY_TARGET_RES_HEIGHT = "key_target_res_height"
        private const val KEY_FILE_NAME = "key_file_name"
        private const val KEY_COMPRESS_QUALITY = "key_compress_quality"

        private const val KEY_FAILURE_MESSAGE = "key_failure_msg"
        private const val KEY_SAVED_IMAGE_URI = "key_saved_img_uri"

        private const val DEFAULT_IMAGE_QUALITY = 80

        fun startWorker(
            context: Context,
            uri: Uri,
            uniqueTag: String?,
            fileName: String?,
            targetQuality: Int,
            targetWidth: Int?,
            targetHeight: Int?,
            compressQuality: CompressQuality
        ) {
            val inputData = Data.Builder()

            inputData.putInt(KEY_TARGET_QUALITY, targetQuality)

            if (fileName != null) {
                inputData.putString(KEY_FILE_PATH, fileName)
            }

            if (targetWidth != null && targetHeight != null) {
                inputData.putInt(KEY_TARGET_RES_WIDTH, targetWidth)
                inputData.putInt(KEY_TARGET_RES_HEIGHT, targetHeight)
            }

            inputData.putString(KEY_FILE_PATH, uri.toString())
            inputData.putInt(KEY_COMPRESS_QUALITY, compressQuality.id)

            val uploadWorkRequest: OneTimeWorkRequest =
                OneTimeWorkRequestBuilder<ImageCompressWorker>()
                    .setInputData(inputData.build())
                    .build()

            WorkManager.getInstance(context)
                .beginUniqueWork(
                    uniqueTag ?: uri.toString(),
                    ExistingWorkPolicy.KEEP,
                    uploadWorkRequest
                )
                .enqueue()
        }
    }

    private val TAG = "ImageCompressWorker"

    private lateinit var fileUri: Uri

    override suspend fun doWork(): Result {

        val filePath = inputData.getString(KEY_FILE_PATH)
        Log.d(TAG, "doWork: Called. File path: $filePath")

        if (filePath.isNullOrEmpty()) {
            Log.e(TAG, "doWork: File path is null")
            return onFailure("File path is null")
        }

        try {
            fileUri = Uri.parse(filePath)
        } catch (e: Exception) {
            Log.d(TAG, "doWork: Exception while parsing file uri")
            return onFailure("Exception while parsing file uri: $e")
        }

        val targetWidth = inputData.getInt(KEY_TARGET_RES_WIDTH, -1)
        val targetHeight: Int = inputData.getInt(KEY_TARGET_RES_HEIGHT, -1)
        val targetQuality = inputData.getInt(KEY_TARGET_QUALITY, DEFAULT_IMAGE_QUALITY)
        val compressQuality: CompressQuality

        inputData.getInt(KEY_COMPRESS_QUALITY, -1).let { qualityId ->
            compressQuality = when (qualityId) {
                CompressQuality.LOW.id -> CompressQuality.LOW
                CompressQuality.MEDIUM.id -> CompressQuality.MEDIUM
                CompressQuality.HIGH.id -> CompressQuality.HIGH
                else -> CompressQuality.MEDIUM
            }
        }

        return performCompression(
            targetFileName = inputData.getString(
                KEY_FILE_NAME
            ),
            targetResolution = if (targetHeight != -1 && targetWidth != -1) Pair(
                targetWidth,
                targetHeight
            ) else null,
            targetImgQuality = targetQuality,
            compressQuality = compressQuality
        )
    }

    private fun performCompression(
        targetFileName: String?,    // If null, original file name will be used
        targetImgQuality: Int,
        compressQuality: CompressQuality,
        targetResolution: Pair<Int, Int>?
    ): Result {
        var parcelFileDescriptor: ParcelFileDescriptor? = null

        try {
            val options: BitmapFactory.Options = BitmapFactory.Options()
            options.inJustDecodeBounds = false
            // Open the file descriptor
            parcelFileDescriptor =
                applicationContext.contentResolver.openFileDescriptor(fileUri, "r")

            if (parcelFileDescriptor == null) {
                Log.d(TAG, "getBitmapFromUriUsingFileDescriptor: File descriptor null")
                return onFailure(msg = "Parcel file descriptor is null")
            }

            val fd = parcelFileDescriptor.fileDescriptor

            BitmapFactory.decodeFileDescriptor(fd, null, options)

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false

            val originalBm = BitmapFactory.decodeFileDescriptor(
                parcelFileDescriptor.fileDescriptor,
                null,
                options
            )

            val width = originalBm.width
            val height = originalBm.height

            var newWidth: Int = targetResolution?.first ?: -1
            var newHeight: Int = targetResolution?.second ?: -1

            if (newWidth == -1 || newHeight == -1) {
                val ratio: Float
                if (width > height) {
                    val maxEdge = width * compressQuality.targetMaxDimensionMultiplier
                    if (width > maxEdge) {
                        newWidth = maxEdge.toInt()
                        ratio = newWidth / width.toFloat()
                        newHeight = (ratio * height).toInt()
                    }
                } else {
                    val maxEdge = height * compressQuality.targetMaxDimensionMultiplier
                    if (height > maxEdge) {
                        newHeight = maxEdge.toInt()
                        ratio = newHeight / height.toFloat()
                        newWidth = (ratio * width).toInt()
                    }
                }
            }

            Log.d(
                TAG,
                "performCompression: Original Res: ${originalBm.width}X${originalBm.height}, New Res: ${newWidth}X${newHeight}, target quality: $targetImgQuality"
            )

            // Get the scaled bitmap
            val scaledBitmap: Bitmap =
                FileUtils.getResizedAndRotatedBitmap(
                    bm = originalBm,
                    exifInterface = FileUtils.getExifInterfaceFromUri(
                        applicationContext.contentResolver,
                        fileUri
                    ),
                    targetWidth = newWidth,
                    targetHeight = newHeight
                )

            if (scaledBitmap != originalBm && !originalBm.isRecycled) {
                Log.d(TAG, "performCompression: Recycling original bitmap")
                originalBm.recycle()
            }

            val fileExtension: String

            var originalFileName: String? = try {
                FileUtils.getFileNameFromUri(applicationContext, fileUri)
            } catch (e: Exception) {
                Log.e(TAG, "performCompression: Exception while querying original file name", e)
                e.printStackTrace()
                null
            }

            if (originalFileName == null) {
                originalFileName = "${System.currentTimeMillis()}.jpg"
            }

            fileExtension = MimeTypeMap.getFileExtensionFromUrl(originalFileName) ?: "jpg"
            val finalFileName =
                if (targetFileName != null) "$targetFileName.$fileExtension" else originalFileName

            Log.d(
                TAG,
                "getBitmapFromUriUsingFileDescriptor: File name: $finalFileName. Saving the file now"
            )

            val savedImgUri = FileUtils.compressAndSaveBitmapToGallery(
                applicationContext,
                fileName = finalFileName,
                bitmap = scaledBitmap,
                quality = targetImgQuality
            )

            Log.d(TAG, "getBitmapFromUriUsingFileDescriptor: Saved: $savedImgUri")
            return onSuccess(
                uri = savedImgUri
            )
        } catch (e: Exception) {
            Log.e(TAG, "performCompression: Failed", e)
            return onFailure(msg = "Something went wrong. Reason: $e")
        } finally {
            parcelFileDescriptor?.close()
        }
    }

    private fun onFailure(msg: String): Result {
        return Result.failure(
            Data.Builder()
                .putString(KEY_FAILURE_MESSAGE, msg)
                .build()
        )
    }

    private fun onSuccess(uri: Uri): Result {
        return Result.success(
            Data.Builder()
                .putString(KEY_SAVED_IMAGE_URI, uri.toString())
                .build()
        )
    }
}