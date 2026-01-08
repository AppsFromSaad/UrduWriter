package com.sf.urduwriter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.sf.urduwriter.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Add your code here, for example:
        // setSupportActionBar(binding.toolbar)
    }
}
