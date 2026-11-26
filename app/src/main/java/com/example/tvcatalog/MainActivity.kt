package com.example.tvcatalog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.tvcatalog.data.Library
import com.example.tvcatalog.data.Repository
import com.example.tvcatalog.ui.CatalogApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repository.init(applicationContext)
        Library.init(applicationContext)
        setContent { CatalogApp() }
    }
}
