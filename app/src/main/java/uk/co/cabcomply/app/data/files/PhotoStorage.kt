package uk.co.cabcomply.app.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import uk.co.cabcomply.app.util.Ids

/**
 * Stores defect/document photos as compressed JPEGs plus a small thumbnail inside the app's
 * private files directory, referenced by [AttachmentEntity.filePath] as a path relative to that
 * directory (product spec section 23: keep normal use from consuming excessive storage, and
 * section 47: relative paths so a restored backup relocates cleanly).
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val photosDir get() = File(context.filesDir, "photos").apply { mkdirs() }

    /** Downscales and re-encodes an image at [sourceUri] (correcting EXIF orientation), returning relative paths. */
    suspend fun importPhoto(sourceUri: Uri, maxDimension: Int = 1600): StoredPhoto = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { it.readBytes() }
            ?: error("Could not read selected photo.")

        val orientation = runCatching {
            ExifInterface(bytes.inputStream()).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("Unsupported image format.")
        val rotated = rotateForExif(original, orientation)
        val scaled = downscale(rotated, maxDimension)
        val thumb = downscale(rotated, 320)

        val id = Ids.newId()
        val fullPath = "photo_$id.jpg"
        val thumbPath = "photo_${id}_thumb.jpg"
        writeJpeg(scaled, File(photosDir, fullPath))
        writeJpeg(thumb, File(photosDir, thumbPath))

        StoredPhoto(relativePath = "photos/$fullPath", thumbnailRelativePath = "photos/$thumbPath")
    }

    fun absoluteFile(relativePath: String): File = File(context.filesDir, relativePath)

    fun delete(relativePath: String) {
        absoluteFile(relativePath).delete()
    }

    private fun rotateForExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = android.graphics.Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun writeJpeg(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
        }
    }
}

data class StoredPhoto(val relativePath: String, val thumbnailRelativePath: String)
