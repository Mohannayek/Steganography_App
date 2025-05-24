package com.tnu.steganography

import android.Manifest
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.tnu.steganography.databinding.ActivityDecodeBinding
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import androidx.appcompat.app.AlertDialog

class DecodeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDecodeBinding
    private var selectedImageUri: Uri? = null
    private var loadedBitmap: Bitmap? = null

    private val STORAGE_PERMISSION_CODE = 102

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            try {
                loadedBitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                binding.selectedImageView.setImageURI(it)
                Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDecodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.chooseImageButton.setOnClickListener {
            // Check for the appropriate permission based on Android version
            if (checkAndRequestPermissions()) {
                pickImage.launch("image/*")
            }
        }

        binding.decodeButton.setOnClickListener {
            val secretKey = binding.keyInput.text.toString()

            if (selectedImageUri == null || loadedBitmap == null) {
                Toast.makeText(this, "Please select an image first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (secretKey.isBlank()) {
                binding.keyInput.error = "Key cannot be empty"
                return@setOnClickListener
            }

            try {
                val decodedMessage = decodeMessageFromBitmap(loadedBitmap!!, secretKey)
                if (decodedMessage.isNotEmpty()) {
                    showDecryptedMessageDialog(decodedMessage)
                } else {
                    Toast.makeText(this, "No message found or incorrect key. (Empty message extracted)", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Decoding failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- NEW / MODIFIED Permission Handling ---
    private fun checkAndRequestPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33 (Android 13) and above
            val readMediaImagesPermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_MEDIA_IMAGES
            )
            if (readMediaImagesPermission == PackageManager.PERMISSION_GRANTED) {
                return true // Already granted
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES), STORAGE_PERMISSION_CODE
                )
                return false // Permission not granted, requested
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { // API 23 (Android 6.0) to API 32 (Android 12)
            val readExternalStoragePermission = ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (readExternalStoragePermission == PackageManager.PERMISSION_GRANTED) {
                return true // Already granted
            } else {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE
                )
                return false // Permission not granted, requested
            }
        } else {
            return true // Permissions are granted by default on pre-Marshmallow
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
                pickImage.launch("image/*") // Launch picker after permission is granted
            } else {
                Toast.makeText(this, "Storage Permission Denied. Cannot load image.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Core Decoding Logic (remains the same) ---
    private fun decodeMessageFromBitmap(encodedBitmap: Bitmap, secretKey: String): String {
        val pixels = IntArray(encodedBitmap.width * encodedBitmap.height)
        encodedBitmap.getPixels(pixels, 0, encodedBitmap.width, 0, 0, encodedBitmap.width, encodedBitmap.height)

        val bitsPerPixel = 4 // As used in encoding (1 LSB from A, R, G, B)

        val extractedDataBytes = mutableListOf<Byte>()
        var currentExtractedByte = 0
        var extractedBitCounter = 0

        var dataLength = -1
        val lengthBuffer = ByteBuffer.allocate(4)

        val keyHash = secretKey.hashCode()
        var pixelOffset = Math.abs(keyHash) % pixels.size

        var bitsForLength = 0
        while (bitsForLength < 32) {
            val currentPixelIndex = (pixelOffset % pixels.size).coerceAtLeast(0)
            val pixel = pixels[currentPixelIndex]

            val A = (pixel shr 24) and 0xFF
            val R = (pixel shr 16) and 0xFF
            val G = (pixel shr 8) and 0xFF
            val B = pixel and 0xFF

            for (channelIndex in 0 until bitsPerPixel) {
                if (bitsForLength >= 32) break

                val extractedBit = when (channelIndex) {
                    0 -> R and 0x01
                    1 -> G and 0x01
                    2 -> B and 0x01
                    3 -> A and 0x01
                    else -> 0
                }

                val byteIndexInLength = bitsForLength / 8
                val bitPositionInByte = 7 - (bitsForLength % 8)
                val currentByteValue = lengthBuffer.get(byteIndexInLength).toInt()
                val newByteValue = currentByteValue or (extractedBit shl bitPositionInByte)
                lengthBuffer.put(byteIndexInLength, newByteValue.toByte())

                bitsForLength++
            }
            pixelOffset++
        }
        dataLength = lengthBuffer.getInt(0)

        if (dataLength < 0 || dataLength > (pixels.size * bitsPerPixel / 8)) {
            throw IllegalArgumentException("Could not extract valid message length. Incorrect key or no message embedded.")
        }

        var bytesExtracted = 0
        currentExtractedByte = 0
        extractedBitCounter = 0

        while (bytesExtracted < dataLength) {
            val currentPixelIndex = (pixelOffset % pixels.size).coerceAtLeast(0)
            val pixel = pixels[currentPixelIndex]

            val A = (pixel shr 24) and 0xFF
            val R = (pixel shr 16) and 0xFF
            val G = (pixel shr 8) and 0xFF
            val B = pixel and 0xFF

            for (channelIndex in 0 until bitsPerPixel) {
                if (bytesExtracted >= dataLength && extractedBitCounter == 0) break

                val extractedBit = when (channelIndex) {
                    0 -> R and 0x01
                    1 -> G and 0x01
                    2 -> B and 0x01
                    3 -> A and 0x01
                    else -> 0
                }

                currentExtractedByte = (currentExtractedByte shl 1) or extractedBit
                extractedBitCounter++

                if (extractedBitCounter == 8) {
                    extractedDataBytes.add(currentExtractedByte.toByte())
                    bytesExtracted++
                    currentExtractedByte = 0
                    extractedBitCounter = 0
                }
            }
            pixelOffset++
        }

        return String(extractedDataBytes.toByteArray(), StandardCharsets.UTF_8)
    }


    private fun showDecryptedMessageDialog(message: String) {
        try {
            val dialog = MessageDecryptSuccessDialog(message)
            dialog.show(supportFragmentManager, "MessageDecryptSuccessDialog")
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Decrypted Message")
                .setMessage(message)
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
            Toast.makeText(this, "Could not show custom dialog. Displaying basic dialog.", Toast.LENGTH_SHORT).show()
        }
    }
}