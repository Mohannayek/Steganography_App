package com.tnu.steganography

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.tnu.steganography.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get SharedPreferences instance
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false) // Default to false (light mode)

        // Initialize the switch state based on saved preference
        binding.themeSwitch.isChecked = isDarkMode

        // Set listener for theme switch
        binding.themeSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                // Dark mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedPrefs.edit().putBoolean("dark_mode", true).apply()
            } else {
                // Light mode
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                sharedPrefs.edit().putBoolean("dark_mode", false).apply()
            }
            // Recreate the activity to apply the theme change immediately
            recreate()
        }

        // Set listener for About Us button
        binding.aboutUsButton.setOnClickListener {
            val intent = Intent(this, AboutUsActivity::class.java)
            startActivity(intent)
        }
    }
}