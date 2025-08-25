package com.xfire.textlinker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // If you're using a toolbar or other UI setup, you can do it here.
        // For now, this just hosts the NavHostFragment from activity_main.xml
    }
}