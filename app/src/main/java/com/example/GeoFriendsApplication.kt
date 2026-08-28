package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class GeoFriendsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase(this)
    }

    companion object {
        fun initFirebase(context: Context) {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) {
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (e: Exception) {
                        Log.w("GeoFriendsApp", "Standard initializeApp failed: ${e.message}")
                    }
                }
                if (FirebaseApp.getApps(context).isEmpty()) {
                    val options = FirebaseOptions.Builder()
                        .setApplicationId("1:736195829305:android:37afad81efe65a553df7e8")
                        .setApiKey("AIzaSyDNzM-JucreE92yWELBWA8bq-Ihm2YeSlA")
                        .setProjectId("geofriends-c32ea")
                        .setDatabaseUrl("https://geofriends-c32ea-default-rtdb.asia-southeast1.firebasedatabase.app")
                        .setStorageBucket("geofriends-c32ea.firebasestorage.app")
                        .setGcmSenderId("736195829305")
                        .build()
                    FirebaseApp.initializeApp(context, options)
                    Log.i("GeoFriendsApp", "Fallback explicit FirebaseApp initialized")
                }
            } catch (e: Exception) {
                Log.e("GeoFriendsApp", "FirebaseApp init error: ${e.message}")
            }
        }
    }
}
