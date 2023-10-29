package com.harsh.squishypics.utils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException


object FileUtils {

    // Returns the uri of the saved image
    fun compressAndSaveBitmapToGallery(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        quality: Int
    ): Uri {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val collection =
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "images/jpeg")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}${File.separator}${
                        context.packageManager.getApplicationLabel(context.applicationInfo)
                    }"
                )
            }

            val uri = context.contentResolver.insert(collection, contentValues)

            context.contentResolver.openOutputStream(uri!!).use { outputStream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)) {
                    throw IOException("Couldn't save bitmap")
                }
                outputStream?.flush()
            }

            return uri
        } else {
            val savedImageURL: String = MediaStore.Images.Media.insertImage(
                context.contentResolver,
                bitmap,
                fileName,
                null
            )
            return Uri.parse(savedImageURL)
        }
    }

    fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    /**
     * @param bm
     * @param maxEdge longest side you need for the output image (aspect ration is maintained)
     * @return
     */
    fun getResizedAndRotatedBitmap(
        bm: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        exifInterface: ExifInterface?
    ): Bitmap {

        var rotation = 0
        if (exifInterface != null) {
            rotation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
            when (rotation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> {
                    rotation = 90
                }

                ExifInterface.ORIENTATION_ROTATE_180 -> {
                    rotation = 180
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> {
                    rotation = 270
                }

                ExifInterface.ORIENTATION_NORMAL -> rotation = 0
            }
        }

        return getResizedAndRotatedBitmap(
            bm,
            targetWidth,
            targetHeight,
            rotation
        )
    }

    private fun getResizedAndRotatedBitmap(
        bm: Bitmap,
        newWidth: Int,
        newHeight: Int,
        rotation: Int
    ): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)
        if (rotation != 0) {
            matrix.postRotate(rotation.toFloat())
        }

        // "RECREATE" THE NEW BITMAP
        return Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, true
        )
    }

    fun getExifInterfaceFromUri(contentResolver: ContentResolver, uri: Uri): ExifInterface? {
        try {
            contentResolver.openInputStream(uri).use { inputStream ->
                return ExifInterface(inputStream!!)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return null
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        val scheme = uri.scheme
        if (scheme == "file") {
            return uri.lastPathSegment
        } else if (scheme == "content") {
            val proj = arrayOf(OpenableColumns.DISPLAY_NAME)
            val cursor = context.contentResolver.query(uri, proj, null, null, null)
            if (cursor != null && cursor.count != 0) {
                val columnIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                return cursor.getString(columnIndex)
            }
            cursor?.close()
        }

        return null
    }

}