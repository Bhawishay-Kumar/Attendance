package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class AttendanceApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }.onFailure { e ->
            Log.w("AttendanceApplication", "FirebaseApp initialization deferred or skipped: ${e.message}")
        }
    }
}
