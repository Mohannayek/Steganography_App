package com.tnu.steganography

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.tnu.steganography.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // @SuppressLint("MissingSuperCall") is not needed and can be removed once super.onCreate is correctly placed
    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Apply saved theme first (this is correct)
        val sharedPrefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isDarkMode = sharedPrefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }

        // 2. Call super.onCreate() ONLY ONCE, after theme setup
        super.onCreate(savedInstanceState)

        // 3. Inflate layout and set content view ONLY ONCE
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 4. Set up your button click listeners and other UI logic below this point
        binding.encodeButton.setOnClickListener {
            val intent = Intent(this, EncodeActivity::class.java)
            startActivity(intent)
        }

        binding.decodeButton.setOnClickListener {
            val intent = Intent(this, DecodeActivity::class.java)
            startActivity(intent)
        }

        binding.bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // This is the home screen, typically no action needed here.
                    true
                }
                R.id.nav_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    startActivity(intent)
                    true
                }
                else -> false
            }
        }
    }
}