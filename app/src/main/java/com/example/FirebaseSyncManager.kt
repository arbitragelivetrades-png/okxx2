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

    const val CURRENT_VERSION_CODE = 2

    data class UpdateConfig(
        val latestVersionCode: Int = 1,
        val latestVersionName: String = "1.0",
        val downloadUrl: String = "",
        val releaseNotes: String = "",
        val isForceUpdate: Boolean = false,
        val logoType: String = "OKX",
        val customLogoText: String = "",
        val maintenanceMode: Boolean = false,
        val maintenanceMessage: String = "",
        val githubRepoUrl: String = ""
    )

    private var firestore: FirebaseFirestore? = null
    private var balanceListener: ListenerRegistration? = null
    private var updateListener: ListenerRegistration? = null

    // Check if configuration is saved or if fallback credentials are available
    fun isConfigured(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val hasCustom = !prefs.getString(KEY_API_KEY, "").isNullOrBlank() &&
                !prefs.getString(KEY_PROJECT_ID, "").isNullOrBlank() &&
                !prefs.getString(KEY_APP_ID, "").isNullOrBlank()
        return hasCustom || (DEFAULT_API_KEY.isNotBlank() && DEFAULT_PROJECT_ID.isNotBlank() && DEFAULT_APP_ID.isNotBlank())
    }

    // Reset active memory instance so next initialize() will pick up the new credentials
    fun resetMemoryInstance(context: Context) {
        firestore = null
        balanceListener?.remove()
        balanceListener = null
        updateListener?.remove()
        updateListener = null
        try {
            FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting FirebaseApp instance during reset", e)
        }
    }

    // Save configuration
    fun saveConfig(context: Context, apiKey: String, projectId: String, appId: String) {
        resetMemoryInstance(context)
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
        resetMemoryInstance(context)
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
                val options = existingApp.options
                if (options.apiKey != apiKey || options.projectId != projectId || options.applicationId != appId) {
                    existingApp.delete()
                    val newOptions = FirebaseOptions.Builder()
                        .setApiKey(apiKey)
                        .setProjectId(projectId)
                        .setApplicationId(appId)
                        .build()
                    FirebaseApp.initializeApp(context, newOptions, APP_NAME)
                } else {
                    existingApp
                }
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

    // Helper to get the fully resolved download URL for update APKs
    fun getResolvedDownloadUrl(config: UpdateConfig): String {
        var url = config.downloadUrl.trim()
        if (url.isBlank() || url.lowercase() == "auto" || url.contains("CHANGE_TO_YOUR_GITHUB_USERNAME")) {
            val githubUrl = config.githubRepoUrl.ifBlank { "https://github.com/arbitragelivetrades/your-repo" }
            val ownerRepo = getOwnerAndRepo(githubUrl)
            if (ownerRepo != null) {
                url = "https://raw.githubusercontent.com/${ownerRepo.first}/${ownerRepo.second}/main/app-release.apk"
            }
        }
        return url
    }

    // Synchronize current local state to Firebase Firestore (User + Balances)
    fun syncUserToCloud(context: Context, user: User, balances: Map<String, Double>, lastUpdatedTimestamp: Long = System.currentTimeMillis()) {
        if (!initialize(context)) return
        val fs = firestore ?: return

        val data = hashMapOf(
            "email" to user.email,
            "username" to user.username,
            "password" to user.password,
            "balances" to balances,
            "lastUpdated" to lastUpdatedTimestamp
        )

        fs.collection("users")
            .document(user.email.lowercase())
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Synced user ${user.email} and balances to Cloud database successfully with timestamp $lastUpdatedTimestamp.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to sync user ${user.email} to Cloud database", e)
            }
    }

    // Start listening in real-time to balances of the logged-in user from Firestore
    fun startRealtimeSync(context: Context, userEmail: String, onBalanceUpdated: (Map<String, Double>, Long) -> Unit) {
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
                    val lastUpdated = snapshot.getLong("lastUpdated") ?: 0L
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
                        Log.d(TAG, "Real-time sync: Received updated balances from Cloud: $convertedBalances, lastUpdated: $lastUpdated")
                        onBalanceUpdated(convertedBalances, lastUpdated)
                    }
                }
            }
    }

    fun checkAppUpdate(context: Context, onConfigUpdated: (UpdateConfig) -> Unit) {
        if (!initialize(context)) return
        val fs = firestore ?: return

        updateListener?.remove()
        updateListener = fs.collection("app_metadata")
            .document("config")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to app update config", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val latestVersionCode = (snapshot.get("latestVersionCode") as? Number)?.toInt() ?: 1
                    val latestVersionName = snapshot.get("latestVersionName") as? String ?: "1.0"
                    val downloadUrl = snapshot.get("downloadUrl") as? String ?: ""
                    val releaseNotes = snapshot.get("releaseNotes") as? String ?: ""
                    val isForceUpdate = snapshot.getBoolean("isForceUpdate") ?: false
                    val logoType = snapshot.getString("logoType") ?: "OKX"
                    val customLogoText = snapshot.getString("customLogoText") ?: ""
                    val maintenanceMode = snapshot.getBoolean("maintenanceMode") ?: false
                    val maintenanceMessage = snapshot.getString("maintenanceMessage") ?: ""
                    val githubRepoUrl = snapshot.getString("githubRepoUrl") ?: ""

                    val baseConfig = UpdateConfig(
                        latestVersionCode = latestVersionCode,
                        latestVersionName = latestVersionName,
                        downloadUrl = downloadUrl,
                        releaseNotes = releaseNotes,
                        isForceUpdate = isForceUpdate,
                        logoType = logoType,
                        customLogoText = customLogoText,
                        maintenanceMode = maintenanceMode,
                        maintenanceMessage = maintenanceMessage,
                        githubRepoUrl = githubRepoUrl
                    )

                    if (githubRepoUrl.isNotBlank()) {
                        fetchGitHubVersionInfo(githubRepoUrl) { githubConfig ->
                            if (githubConfig != null && githubConfig.latestVersionCode > latestVersionCode) {
                                onConfigUpdated(baseConfig.copy(
                                    latestVersionCode = githubConfig.latestVersionCode,
                                    latestVersionName = githubConfig.latestVersionName,
                                    downloadUrl = githubConfig.downloadUrl,
                                    releaseNotes = githubConfig.releaseNotes
                                ))
                            } else {
                                onConfigUpdated(baseConfig)
                            }
                        }
                    } else {
                        onConfigUpdated(baseConfig)
                    }
                } else {
                    onConfigUpdated(UpdateConfig())
                }
            }
    }

    // Stop active listeners
    fun stopRealtimeSync() {
        balanceListener?.remove()
        balanceListener = null
        updateListener?.remove()
        updateListener = null
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

    data class CloudUserData(
        val user: User,
        val balances: Map<String, Double>
    )

    suspend fun fetchUserDataFromCloud(context: Context, email: String): CloudUserData? {
        if (!initialize(context)) return null
        val fs = firestore ?: return null
        return try {
            val task = fs.collection("users").document(email.lowercase()).get()
            val document = com.google.android.gms.tasks.Tasks.await(task)
            if (document != null && document.exists()) {
                val dbEmail = document.getString("email") ?: email
                val username = document.getString("username") ?: ""
                val password = document.getString("password") ?: ""
                val user = User(dbEmail, username, password)

                @Suppress("UNCHECKED_CAST")
                val cloudBalances = document.get("balances") as? Map<String, Any>
                val balances = if (cloudBalances != null) {
                    cloudBalances.mapValues { entry ->
                        when (val value = entry.value) {
                            is Number -> value.toDouble()
                            is String -> value.toDoubleOrNull() ?: 0.0
                            else -> 0.0
                        }
                    }
                } else {
                    emptyMap()
                }

                CloudUserData(user, balances)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user data from cloud for $email", e)
            null
        }
    }

    fun fetchGitHubVersionInfo(githubRepoUrl: String, onComplete: (UpdateConfig?) -> Unit) {
        val ownerRepo = getOwnerAndRepo(githubRepoUrl) ?: return onComplete(null)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        Thread {
            try {
                var branch = "main"
                var url = java.net.URL("https://raw.githubusercontent.com/${ownerRepo.first}/${ownerRepo.second}/main/app_version.json")
                var connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                var responseCode = connection.responseCode
                if (responseCode == 404) {
                    branch = "master"
                    url = java.net.URL("https://raw.githubusercontent.com/${ownerRepo.first}/${ownerRepo.second}/master/app_version.json")
                    connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    responseCode = connection.responseCode
                }
                
                if (responseCode == 200) {
                    val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                    val githubInfo = parseSimpleJson(jsonText, githubRepoUrl, branch)
                    mainHandler.post {
                        if (githubInfo != null) {
                            onComplete(UpdateConfig(
                                latestVersionCode = githubInfo.versionCode,
                                latestVersionName = githubInfo.versionName,
                                downloadUrl = githubInfo.downloadUrl,
                                releaseNotes = githubInfo.releaseNotes,
                                githubRepoUrl = githubRepoUrl
                            ))
                        } else {
                            onComplete(null)
                        }
                    }
                } else {
                    mainHandler.post { onComplete(null) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching version info from GitHub: ${e.message}")
                mainHandler.post { onComplete(null) }
            }
        }.start()
    }

    private fun getOwnerAndRepo(githubRepoUrl: String): Pair<String, String>? {
        val cleanUrl = githubRepoUrl.trim().removeSuffix("/")
        if (!cleanUrl.contains("github.com/")) return null
        val parts = cleanUrl.split("github.com/")
        if (parts.size < 2) return null
        val pathParts = parts[1].split("/")
        if (pathParts.size < 2) return null
        val owner = pathParts[0]
        val repo = pathParts[1]
        return Pair(owner, repo)
    }

    private fun parseGitHubRawUrl(githubRepoUrl: String): String? {
        val ownerRepo = getOwnerAndRepo(githubRepoUrl) ?: return null
        return "https://raw.githubusercontent.com/${ownerRepo.first}/${ownerRepo.second}/main/app_version.json"
    }

    private fun parseSimpleJson(jsonString: String, githubRepoUrl: String, branch: String = "main"): GitHubVersionInfo? {
        try {
            val versionCodeStr = findJsonValue(jsonString, "versionCode") ?: return null
            val versionName = findJsonValue(jsonString, "versionName") ?: "1.0"
            var downloadUrl = findJsonValue(jsonString, "downloadUrl") ?: ""
            val releaseNotes = findJsonValue(jsonString, "releaseNotes") ?: ""
            val versionCode = versionCodeStr.toIntOrNull() ?: 1

            if (downloadUrl.isBlank() || downloadUrl.lowercase() == "auto" || downloadUrl.contains("CHANGE_TO_YOUR_GITHUB_USERNAME")) {
                val ownerRepo = getOwnerAndRepo(githubRepoUrl)
                if (ownerRepo != null) {
                    downloadUrl = "https://raw.githubusercontent.com/${ownerRepo.first}/${ownerRepo.second}/$branch/app-release.apk"
                }
            }

            return GitHubVersionInfo(versionCode, versionName, downloadUrl, releaseNotes)
        } catch (e: Exception) {
            return null
        }
    }

    private fun findJsonValue(json: String, key: String): String? {
        val pattern = "\"$key\"\\s*:\\s*\"?([^\",}]+)\"?"
        val regex = pattern.toRegex()
        val matchResult = regex.find(json)
        return matchResult?.groupValues?.get(1)?.trim()
    }

    data class GitHubVersionInfo(
        val versionCode: Int,
        val versionName: String,
        val downloadUrl: String,
        val releaseNotes: String
    )
}
