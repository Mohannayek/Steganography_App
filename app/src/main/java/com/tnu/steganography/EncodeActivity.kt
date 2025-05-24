package com.tnu.steganography

import java.io.File // Add this line
import java.io.FileOutputStream // Add this line
import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tnu.steganography.databinding.ActivityEncodeBinding
import java.io.OutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets // Import for StandardCharsets

class EncodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEncodeBinding
    private var selectedImageUri: Uri? = null
    private var originalBitmap: Bitmap? = null // To hold the original image Bitmap

    // Request code for storage permissions
    private val STORAGE_PERMISSION_CODE = 101

    // Activity Result Launcher for picking an image
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                // Load the bitmap from the URI
                originalBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                binding.selectedImageView.setImageBitmap(originalBitmap)
                Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEncodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.chooseImageButton.setOnClickListener {
            pickImage.launch("image/*")
        }

        binding.encodeAndSaveButton.setOnClickListener {
            val message = binding.messageInput.text.toString()
            val secretKey = binding.secretKeyInput.text.toString()

            if (selectedImageUri == null || originalBitmap == null) {
                Toast.makeText(this, "Please select an image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (message.isBlank()) {
                binding.messageInput.error = "Message cannot be empty"
                return@setOnClickListener
            }
            if (secretKey.isBlank()) {
                binding.secretKeyInput.error = "Secret Key cannot be empty"
                return@setOnClickListener
            }

            // Check and request permission before attempting to save
            if (checkPermission()) {
                performEncodingAndSaving(message, secretKey)
            } else {
                requestPermission()
            }
        }
    }

    // --- Permission Handling ---
    private fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 10 (API 29) and above, requestLegacyExternalStorage="true"
            // still requires WRITE_EXTERNAL_STORAGE for broad access.
            // For saving to MediaStore, we don't strictly need WRITE_EXTERNAL_STORAGE,
            // but for older approaches or if requestLegacyExternalStorage is not fully working,
            // it's safer to check. MediaStore insert handles permissions implicitly.
            true // For Q+ with MediaStore, permissions handled differently. Assuming MediaStore is used.
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // Android 6.0 - 9.0
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Permissions are granted by default on pre-Marshmallow
        }
    }

    private fun requestPermission() {
        // Request WRITE_EXTERNAL_STORAGE for older Android versions
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        } else {
            // On Android 10 (Q) and above, rely on MediaStore for saving.
            // If requestLegacyExternalStorage="true" is set, the WRITE permission might still be needed
            // depending on exact save location. For simple saving to public directories
            // via MediaStore, explicit permission request is often not required,
            // but the system might prompt if the target directory is special.
            Toast.makeText(this, "Saving to MediaStore...", Toast.LENGTH_SHORT).show()
            performEncodingAndSaving(binding.messageInput.text.toString(), binding.secretKeyInput.text.toString())
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permission Granted", Toast.LENGTH_SHORT).show()
                performEncodingAndSaving(binding.messageInput.text.toString(), binding.secretKeyInput.text.toString())
            } else {
                Toast.makeText(this, "Storage Permission Denied. Cannot save image.", Toast.LENGTH_LONG).show()
            }
        }
    }


    // --- Core Encoding and Saving Logic ---

    private fun performEncodingAndSaving(message: String, secretKey: String) {
        // Create a mutable copy of the bitmap for pixel modification
        val mutableBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)

        if (mutableBitmap == null) {
            Toast.makeText(this, "Failed to get mutable image.", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val encodedBitmap = encodeMessageIntoBitmap(mutableBitmap, message, secretKey)
            saveBitmapToGallery(encodedBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Encoding or Saving failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Implements a simple LSB (Least Significant Bit) steganography encoding.
     * This is a basic example and can be improved for robustness and security.
     *
     * @param originalBitmap The mutable bitmap to embed the message into.
     * @param message The secret message string to hide.
     * @param secretKey The secret key (used here simply to influence embedding order/start, can be more complex).
     * @return The bitmap with the message embedded.
     */
    private fun encodeMessageIntoBitmap(originalBitmap: Bitmap, message: String, secretKey: String): Bitmap {
        // Convert message to bytes, then prefix with message length
        val messageBytes = message.toByteArray(StandardCharsets.UTF_8)
        val messageLength = messageBytes.size
        val lengthBytes = ByteBuffer.allocate(4).putInt(messageLength).array() // 4 bytes for length

        // Combine length bytes and message bytes
        val dataToHide = lengthBytes + messageBytes

        val pixels = IntArray(originalBitmap.width * originalBitmap.height)
        originalBitmap.getPixels(pixels, 0, originalBitmap.width, 0, 0, originalBitmap.width, originalBitmap.height)

        var dataIndex = 0
        var bitIndex = 0 // current bit of the current byte in dataToHide

        // We'll embed 1 bit of data per LSB of R, G, B, A channels of each pixel.
        // So, 4 bits per pixel.
        val bitsPerPixel = 4 // One LSB from R, G, B, A

        // Calculate required pixels
        val requiredBits = dataToHide.size * 8
        val availablePixels = pixels.size
        val availableBits = availablePixels * bitsPerPixel

        if (requiredBits > availableBits) {
            throw IllegalArgumentException("Message is too large for the selected image. Max capacity: ${availableBits / 8} bytes.")
        }

        // Simple pseudo-random starting point using the key for demonstration
        // A real implementation would use the key more robustly for bit permutation.
        val keyHash = secretKey.hashCode() // Generate a hash from the key
        var pixelOffset = Math.abs(keyHash) % availablePixels // Use hash to determine a starting offset

        while (dataIndex < dataToHide.size) {
            val currentByte = dataToHide[dataIndex]

            // Iterate through pixels, wrapping around if needed
            val currentPixelIndex = (pixelOffset % availablePixels).coerceAtLeast(0) // Ensure valid index
            val pixel = pixels[currentPixelIndex]

            var A = (pixel shr 24) and 0xFF
            var R = (pixel shr 16) and 0xFF
            var G = (pixel shr 8) and 0xFF
            var B = pixel and 0xFF

            // Hide bits in A, R, G, B channels
            for (channelIndex in 0 until bitsPerPixel) {
                if (dataIndex * 8 + bitIndex >= requiredBits) {
                    break // All data embedded
                }

                val bitToHide = (currentByte.toInt() shr (7 - bitIndex)) and 0x01

                when (channelIndex) {
                    0 -> R = (R and 0xFE) or bitToHide // Modify LSB of Red
                    1 -> G = (G and 0xFE) or bitToHide // Modify LSB of Green
                    2 -> B = (B and 0xFE) or bitToHide // Modify LSB of Blue
                    3 -> A = (A and 0xFE) or bitToHide // Modify LSB of Alpha
                }

                bitIndex++
            }

            // Update pixel with new A, R, G, B values
            pixels[currentPixelIndex] = (A shl 24) or (R shl 16) or (G shl 8) or B

            if (bitIndex == 8) {
                bitIndex = 0
                dataIndex++
            }
            pixelOffset++ // Move to next pixel (or a determined next pixel by key)
        }

        // Create the new bitmap from the modified pixels
        val encodedBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, originalBitmap.config)
        encodedBitmap.setPixels(pixels, 0, originalBitmap.width, 0, 0, originalBitmap.width, originalBitmap.height)
        return encodedBitmap
    }

    /**
     * Saves the given Bitmap to the device's public Pictures directory using MediaStore.
     * This is the recommended way for Android Q (API 29) and above.
     * For older APIs, it will fall back to using Environment.getExternalStoragePublicDirectory.
     */
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "stego_image_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        var imageUri: Uri? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android Q (API 29) and above
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    put(MediaStore.MediaColumns.IS_PENDING, 1) // Indicate that the file is not yet ready for public use
                }
                imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { contentResolver.openOutputStream(it) }
            } else {
                // Use old method for pre-Android Q
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                if (!imagesDir.exists()) {
                    imagesDir.mkdirs()
                }
                val image = File(imagesDir, filename)
                fos = FileOutputStream(image)
                imageUri = Uri.fromFile(image) // Get URI from file for older method
            }

            fos?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) // Compress the bitmap to PNG format
                Toast.makeText(this, "Image saved to Gallery!", Toast.LENGTH_LONG).show()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && imageUri != null) {
                // Mark the file as ready for public use after saving
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                contentResolver.update(imageUri, contentValues, null, null)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save image: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            fos?.close()
        }
    }
}