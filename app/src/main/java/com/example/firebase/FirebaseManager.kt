package com.example.firebase

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.example.data.Preset
import com.example.data.SleepLog
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FirebaseState(
    val isInitialized: Boolean = false,
    val user: FirebaseUser? = null,
    val userId: String? = null,
    val sleepTipOfTheDay: String = "Keep your room dark and cool (around 65°F / 18°C) for optimal deep sleep quality.",
    val cloudSyncStatus: String = "Ready"
)

object FirebaseManager {

    private const val TAG = "FirebaseManager"

    private var firebaseAnalytics: FirebaseAnalytics? = null
    private var firebaseAuth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null
    private var crashlytics: FirebaseCrashlytics? = null

    private val _state = MutableStateFlow(FirebaseState())
    val state: StateFlow<FirebaseState> = _state.asStateFlow()

    fun initialize(context: Context) {
        try {
            val app = FirebaseApp.initializeApp(context)
            if (app != null) {
                val options = app.options
                val isDummy = options.applicationId.contains("dummy") || options.projectId?.contains("dummy") == true

                if (isDummy) {
                    Log.i(TAG, "Dummy Firebase configuration detected. Running in Local-Only Mode.")
                    try {
                        FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to disable Analytics programmatically", e)
                    }
                    try {
                        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to disable Crashlytics programmatically", e)
                    }
                    _state.value = _state.value.copy(
                        isInitialized = false,
                        cloudSyncStatus = "Local-Only Mode",
                        sleepTipOfTheDay = "Using local storage. Connect to Google Cloud to synchronize settings."
                    )
                    return
                }

                firebaseAnalytics = FirebaseAnalytics.getInstance(context).apply {
                    setAnalyticsCollectionEnabled(true)
                }
                firebaseAuth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                crashlytics = FirebaseCrashlytics.getInstance().apply {
                    setCrashlyticsCollectionEnabled(true)
                    log("FirebaseManager initialized")
                }

                _state.value = _state.value.copy(isInitialized = true)

                // Authenticate Anonymously
                signInAnonymously()
            } else {
                Log.w(TAG, "FirebaseApp initialization returned null")
                _state.value = _state.value.copy(cloudSyncStatus = "Offline Mode")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Firebase", e)
            _state.value = _state.value.copy(cloudSyncStatus = "Offline Mode")
        }
    }



    private fun signInAnonymously() {
        try {
            val auth = firebaseAuth ?: return
            val currentUser = auth.currentUser
            if (currentUser != null) {
                _state.value = _state.value.copy(
                    user = currentUser,
                    userId = currentUser.uid,
                    cloudSyncStatus = "Connected"
                )
            } else {
                auth.signInAnonymously().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        _state.value = _state.value.copy(
                            user = user,
                            userId = user?.uid,
                            cloudSyncStatus = "Connected"
                        )
                        Log.d(TAG, "Signed in anonymously as ${user?.uid}")
                    } else {
                        Log.w(TAG, "Anonymous auth failed", task.exception)
                        _state.value = _state.value.copy(cloudSyncStatus = "Auth Fallback")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during anonymous sign-in", e)
        }
    }

    fun syncPresetToCloud(preset: Preset) {
        val uid = state.value.userId ?: return
        val db = firestore ?: return

        try {
            val presetMap = mapOf(
                "id" to preset.id,
                "name" to preset.name,
                "volumes" to preset.volumes.mapKeys { it.key.name },
                "updatedAt" to System.currentTimeMillis()
            )

            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(preset.id)
                .set(presetMap)
                .addOnSuccessListener {
                    Log.d(TAG, "Preset ${preset.name} synced to Firebase Firestore")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Failed to sync preset to Firestore", e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing preset", e)
        }
    }

    fun deletePresetFromCloud(presetId: String) {
        val uid = state.value.userId ?: return
        val db = firestore ?: return

        try {
            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(presetId)
                .delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting preset from Firestore", e)
        }
    }

    fun syncSleepLogToCloud(log: SleepLog) {
        val uid = state.value.userId ?: return
        val db = firestore ?: return

        try {
            val logMap = mapOf(
                "id" to log.id,
                "timestamp" to log.timestamp,
                "durationMinutes" to log.durationMinutes,
                "presetUsed" to log.presetUsed
            )

            db.collection("users")
                .document(uid)
                .collection("sleep_logs")
                .document(log.id.toString())
                .set(logMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sleep log to Firestore", e)
        }
    }

    fun logAnalyticsEvent(eventName: String, params: Bundle? = null) {
        try {
            firebaseAnalytics?.logEvent(eventName, params)
            Log.d(TAG, "Analytics event logged: $eventName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log analytics event", e)
            logException(e)
        }
    }

    fun logScreenView(screenName: String, screenClass: String = "MainActivity") {
        try {
            val bundle = Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, screenClass)
            }
            firebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            Log.d(TAG, "Screen view logged: $screenName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log screen view", e)
            logException(e)
        }
    }
    
    fun logException(e: Throwable) {
        crashlytics?.recordException(e)
    }

    fun logPlaySoundscape(presetName: String?) {
        val bundle = Bundle().apply {
            putString("preset_name", presetName ?: "Custom Mix")
        }
        logAnalyticsEvent("play_soundscape", bundle)
    }

    fun logTimerStart(minutes: Int) {
        val bundle = Bundle().apply {
            putInt("duration_minutes", minutes)
        }
        logAnalyticsEvent("start_sleep_timer", bundle)
    }

    fun logSleepCompleted(durationMinutes: Int, presetUsed: String) {
        val bundle = Bundle().apply {
            putInt("duration_minutes", durationMinutes)
            putString("preset_used", presetUsed)
        }
        logAnalyticsEvent("sleep_session_completed", bundle)
    }
}
