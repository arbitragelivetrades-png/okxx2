package com.example

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object FirebaseSyncManager {
    private const val TAG = "FirebaseSyncManager"
    private const val PREFS_NAME = "firebase_sync_settings"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_PROJECT_ID = "project_id"
    private const val KEY_APP_ID = "app_id"
    private const val APP_NAME = "dynamic_sync"

    // Default pre-loaded credentials from google-services.json
    private const val DEFAULT_API_KEY = "AIzaSyDOFPOzXZNcPNx9SM-MSMOpyRSfmLhHC1o"
    private const val DEFAULT_PROJECT_ID = "adorable-8e40c"
    private const val DEFAULT_APP_ID = "1:1004621666717:android:940450615c374d4bf17774"

    private var firestore: FirebaseFirestore? = null
    private var balanceListener: ListenerRegistration? = null

    // Check if configuration is saved or if fallback credentials are available
    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasCustom = !prefs.getString(KEY_API_KEY, "").isNullOrBlank() &&
                !prefs.getString(KEY_PROJECT_ID, "").isNullOrBlank() &&
                !prefs.getString(KEY_APP_ID, "").isNullOrBlank()
        return hasCustom || (DEFAULT_API_KEY.isNotBlank() && DEFAULT_PROJECT_ID.isNotBlank() && DEFAULT_APP_ID.isNotBlank())
    }

    // Save configuration
    fun saveConfig(context: Context, apiKey: String, projectId: String, appId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_API_KEY, apiKey.trim())
            .putString(KEY_PROJECT_ID, projectId.trim())
            .putString(KEY_APP_ID, appId.trim())
            .apply()
    }

    // Clear configuration
    fun clearConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        firestore = null
        balanceListener?.remove()
        balanceListener = null
    }

    // Get current config values (falls back to built-in credentials)
    fun getConfig(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedApiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val savedProjectId = prefs.getString(KEY_PROJECT_ID, "") ?: ""
        val savedAppId = prefs.getString(KEY_APP_ID, "") ?: ""
        
        return if (savedApiKey.isNotBlank() && savedProjectId.isNotBlank() && savedAppId.isNotBlank()) {
            Triple(savedApiKey, savedProjectId, savedAppId)
        } else {
            Triple(DEFAULT_API_KEY, DEFAULT_PROJECT_ID, DEFAULT_APP_ID)
        }
    }

    // Initialize Firestore dynamically
    fun initialize(context: Context): Boolean {
        if (firestore != null) return true

        val (apiKey, projectId, appId) = getConfig(context)
        if (apiKey.isBlank() || projectId.isBlank() || appId.isBlank()) {
            Log.d(TAG, "Firebase Firestore is not configured yet.")
            return false
        }

        return try {
            val existingApp = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            val app = if (existingApp != null) {
                existingApp
            } else {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setProjectId(projectId)
                    .setApplicationId(appId)
                    .build()
                FirebaseApp.initializeApp(context, options, APP_NAME)
            }
            firestore = FirebaseFirestore.getInstance(app)
            Log.i(TAG, "Firebase Firestore dynamic initialization successful.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Firestore dynamically", e)
            false
        }
    }

    // Synchronize current local state to Firebase Firestore (User + Balances)
    fun syncUserToCloud(context: Context, user: User, balances: Map<String, Double>) {
        if (!initialize(context)) return
        val fs = firestore ?: return

        val data = hashMapOf(
            "email" to user.email,
            "username" to user.username,
            "password" to user.password,
            "balances" to balances,
            "lastUpdated" to System.currentTimeMillis()
        )

        fs.collection("users")
            .document(user.email.lowercase())
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Synced user ${user.email} and balances to Cloud database successfully.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync user ${user.email} to Cloud database", e)
            }
    }

    // Start listening in real-time to balances of the logged-in user from Firestore
    fun startRealtimeSync(context: Context, userEmail: String, onBalanceUpdated: (Map<String, Double>) -> Unit) {
        if (!initialize(context)) return
        val fs = firestore ?: return

        // Remove any previous listeners
        balanceListener?.remove()

        balanceListener = fs.collection("users")
            .document(userEmail.lowercase())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to cloud balance updates", error)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val cloudBalances = snapshot.get("balances") as? Map<String, Any>
                    if (cloudBalances != null) {
                        val convertedBalances = cloudBalances.mapValues { entry ->
                            when (val value = entry.value) {
                                is Number -> value.toDouble()
                                is String -> value.toDoubleOrNull() ?: 0.0
                                else -> 0.0
                            }
                        }
                        Log.d(TAG, "Real-time sync: Received updated balances from Cloud: $convertedBalances")
                        onBalanceUpdated(convertedBalances)
                    }
                }
            }
    }

    // Stop active listeners
    fun stopRealtimeSync() {
        balanceListener?.remove()
        balanceListener = null
    }

    // Push all local users and balances to Firestore (triggered upon enabling cloud sync)
    fun uploadAllLocalDataToCloud(context: Context, scope: CoroutineScope) {
        if (!initialize(context)) return
        val db = WalletDatabase.getDatabase(context)
        
        scope.launch(Dispatchers.IO) {
            try {
                // Get all balances
                val allBalances = db.coinBalanceDao().getAllBalances()
                val balancesMap = allBalances.associate { it.symbol.uppercase() to it.amount }

                // Get current logged-in user or standard local user
                val prefs = context.getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
                val loggedEmail = prefs.getString("logged_in_user_email", null)
                
                if (loggedEmail != null) {
                    val user = db.userDao().getUserByEmail(loggedEmail)
                    if (user != null) {
                        syncUserToCloud(context, user, balancesMap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing bulk local data sync", e)
            }
        }
    }
}
