package com.example

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WalletDatabase.getDatabase(application)
    private val repository = WalletRepository(database.coinBalanceDao())

    private val networkMonitor = NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // Streamlined user session state
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    // Expose all user coin balances as a flow, converted to a Map
    val coinBalances: StateFlow<Map<String, Double>> = repository.allBalances
        .combine(MutableStateFlow(emptyMap<String, Double>())) { list, _ ->
            list.associate { it.symbol.uppercase() to it.amount }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // Current market prices for supported coins (in USD)
    private val _prices = MutableStateFlow(
        mapOf(
            "USDT" to 1.00,
            "USDG" to 1.00,
            "USDC" to 1.00,
            "BTC" to 62500.00,
            "ETH" to 1800.00,
            "BNB" to 550.00,
            "SOL" to 73.50,
            "TRX" to 0.32,
            "1INCH" to 0.07,
            "XAUT" to 3990.00
        )
    )
    val prices: StateFlow<Map<String, Double>> = _prices

    // Calculated total portfolio balance in USD
    val totalBalanceUsd: StateFlow<Double> = coinBalances
        .combine(prices) { balances, currentPrices ->
            var sum = 0.0
            balances.forEach { (symbol, amount) ->
                val price = currentPrices[symbol] ?: 0.0
                sum += amount * price
            }
            sum
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    private val _showDepositReceived = MutableStateFlow(false)
    val showDepositReceived: StateFlow<Boolean> = _showDepositReceived

    private var isInitialSyncDone = false
    private var depositNotificationJob: kotlinx.coroutines.Job? = null

    fun triggerDepositReceived() {
        depositNotificationJob?.cancel()
        depositNotificationJob = viewModelScope.launch(Dispatchers.Main) {
            _showDepositReceived.value = true
            delay(30000) // 30 seconds
            _showDepositReceived.value = false
        }
    }

    private val client = OkHttpClient()

    init {
        // Load persisted user session if any with Cloud Firestore backup
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = application.getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
            val savedEmail = prefs.getString("logged_in_user_email", null)
            if (savedEmail != null) {
                android.util.Log.d("WalletViewModel", "Found saved email session: $savedEmail. Checking database...")
                // First, check local database
                var user = database.userDao().getUserByEmail(savedEmail)
                
                // If local database doesn't have the user, fetch from cloud
                if (user == null) {
                    android.util.Log.d("WalletViewModel", "User $savedEmail not found in local DB. Checking Cloud Firestore...")
                    val cloudData = FirebaseSyncManager.fetchUserDataFromCloud(application, savedEmail)
                    if (cloudData != null) {
                        user = cloudData.user
                        // Cache locally
                        database.userDao().insertUser(user)
                        // Restore their cloud balances to local database
                        cloudData.balances.forEach { (symbol, amount) ->
                            repository.setBalance(symbol, amount)
                        }
                        android.util.Log.d("WalletViewModel", "User $savedEmail restored successfully from Cloud Firestore.")
                    }
                } else {
                    // If user exists locally, we also fetch from cloud on startup to update local balances and profile
                    android.util.Log.d("WalletViewModel", "User $savedEmail found locally. Syncing with Cloud Firestore...")
                    val cloudData = FirebaseSyncManager.fetchUserDataFromCloud(application, savedEmail)
                    if (cloudData != null) {
                        // Update local user and password details just in case they were updated in admin
                        database.userDao().insertUser(cloudData.user)
                        user = cloudData.user
                        // Sync their balances
                        cloudData.balances.forEach { (symbol, amount) ->
                            repository.setBalance(symbol, amount)
                        }
                        android.util.Log.d("WalletViewModel", "User $savedEmail and balances synced successfully on startup.")
                    }
                }

                if (user != null) {
                    _currentUser.value = user
                    // Start cloud sync listener
                    launch(Dispatchers.Main) {
                        setupCloudSync(application, user)
                    }
                } else {
                    // No user found anywhere, clear the saved session to prevent broken state
                    android.util.Log.w("WalletViewModel", "Session for $savedEmail is invalid. Clearing session.")
                    prefs.edit().remove("logged_in_user_email").apply()
                }
            }
        }

        // Load initial balances if DB is empty to match the OKX look-and-feel of default tiny fractions
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = database.coinBalanceDao().getAllBalances()
            if (currentList.isEmpty()) {
                // Seed database with default small values
                repository.setBalance("USDT", 0.0)
                repository.setBalance("USDG", 0.0)
                repository.setBalance("USDC", 0.0)
                repository.setBalance("BTC", 0.0)
                repository.setBalance("ETH", 0.0)
                repository.setBalance("BNB", 0.0)
                repository.setBalance("SOL", 0.0)
                repository.setBalance("TRX", 0.0)
                repository.setBalance("1INCH", 0.0)
                repository.setBalance("XAUT", 0.0)
            }
        }

        // Start live ticker: periodically fetch real prices from public API
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    fetchRealPrices()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(15000) // update every 15 seconds
            }
        }

        // Start live market fluctuation loop (simulated ticks to make numbers feel active and alive)
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                delay(2000) // tick every 2 seconds
                val currentPrices = _prices.value.toMutableMap()
                currentPrices.forEach { (symbol, price) ->
                    // Stable coins shouldn't fluctuate much, others tick up/down slightly
                    if (symbol != "USDT" && symbol != "USDC" && symbol != "USDG") {
                        val changePercent = 1.0 + (Random.nextDouble(-0.0005, 0.0005)) // +/- 0.05% fluctuation
                        currentPrices[symbol] = price * changePercent
                    }
                }
                _prices.value = currentPrices
            }
        }
    }

    private fun fetchRealPrices() {
        val request = Request.Builder()
            .url("https://api.binance.com/api/v3/ticker/price?symbols=%5B%22BTCUSDT%22,%22ETHUSDT%22,%22BNBUSDT%22,%22SOLUSDT%22,%22TRXUSDT%22,%221INCHUSDT%22,%22USDCUSDT%22,%22XAUTUSDT%22%5D")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return
                val jsonArray = JSONArray(responseBody)
                val newPrices = _prices.value.toMutableMap()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val symbolPair = item.getString("symbol")
                    val priceStr = item.getString("price")
                    val price = priceStr.toDoubleOrNull() ?: continue
                    val coinSymbol = symbolPair.replace("USDT", "").uppercase()
                    newPrices[coinSymbol] = price
                }
                // Explicitly set stablecoins
                newPrices["USDT"] = 1.0
                newPrices["USDC"] = 1.0
                newPrices["USDG"] = 1.0
                _prices.value = newPrices
            }
        }
    }

    fun setupCloudSync(context: Context, user: User) {
        if (FirebaseSyncManager.isConfigured(context)) {
            val prefs = context.getSharedPreferences("okx_sync_prefs", Context.MODE_PRIVATE)
            FirebaseSyncManager.startRealtimeSync(context, user.email) { updatedBalances, lastUpdated ->
                viewModelScope.launch(Dispatchers.IO) {
                    val lastLocalUpdate = prefs.getLong("last_local_update_time", 0L)
                    if (lastUpdated >= lastLocalUpdate) {
                        var balanceIncreased = false
                        val currentMap = coinBalances.value
                        if (isInitialSyncDone && currentMap.isNotEmpty()) {
                            updatedBalances.forEach { (symbol, amount) ->
                                val oldAmount = currentMap[symbol] ?: 0.0
                                if (amount > oldAmount) {
                                    balanceIncreased = true
                                    android.util.Log.d("WalletViewModel", "Deposit detected for $symbol: $oldAmount -> $amount")
                                }
                            }
                        }

                        updatedBalances.forEach { (symbol, amount) ->
                            repository.setBalance(symbol, amount)
                        }

                        if (balanceIncreased) {
                            triggerDepositReceived()
                        }
                        isInitialSyncDone = true
                    } else {
                        android.util.Log.d("WalletViewModel", "Ignoring outdated cloud sync update. Cloud: $lastUpdated, Local: $lastLocalUpdate")
                    }
                }
            }
        }
    }

    fun stopCloudSync() {
        FirebaseSyncManager.stopRealtimeSync()
        isInitialSyncDone = false
        _showDepositReceived.value = false
        depositNotificationJob?.cancel()
    }

    fun triggerCloudBackup() {
        val user = _currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val prefs = getApplication<Application>().getSharedPreferences("okx_sync_prefs", Context.MODE_PRIVATE)
                prefs.edit().putLong("last_local_update_time", now).apply()

                val allBalances = database.coinBalanceDao().getAllBalances()
                val currentBalances = allBalances.associate { it.symbol.uppercase() to it.amount }
                FirebaseSyncManager.syncUserToCloud(getApplication(), user, currentBalances, now)
            } catch (e: Exception) {
                android.util.Log.e("WalletViewModel", "Failed to trigger cloud backup", e)
            }
        }
    }

    fun addBalance(symbol: String, amount: Double) {
        if (!isOnline.value) {
            android.util.Log.e("WalletViewModel", "Cannot add balance while offline.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBalance(symbol.uppercase(), amount)
            // Backup to cloud with the updated balance directly
            triggerCloudBackup()
        }
    }

    fun withdrawBalance(symbol: String, amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (!isOnline.value) {
            viewModelScope.launch(Dispatchers.Main) {
                onError("Restricted: Device is offline")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.withdrawBalance(symbol.uppercase(), amount)
            if (success) {
                triggerCloudBackup()
                launch(Dispatchers.Main) {
                    onSuccess()
                }
            } else {
                launch(Dispatchers.Main) {
                    onError("Insufficient balance")
                }
            }
        }
    }

    fun registerUser(user: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val localExisting = database.userDao().getUserByEmail(user.email)
            if (localExisting != null) {
                launch(Dispatchers.Main) {
                    onError("Email already registered")
                }
                return@launch
            }

            // Check Cloud Firestore for existing email
            val cloudData = FirebaseSyncManager.fetchUserDataFromCloud(getApplication(), user.email)
            if (cloudData != null) {
                launch(Dispatchers.Main) {
                    onError("Email already registered")
                }
                return@launch
            }

            // Proceed with registration
            database.userDao().insertUser(user)
            
            // Persist session
            val prefs = getApplication<Application>().getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
            prefs.edit().putString("logged_in_user_email", user.email).apply()

            launch(Dispatchers.Main) {
                _currentUser.value = user
                setupCloudSync(getApplication(), user)
                triggerCloudBackup()
                onSuccess()
            }
        }
    }

    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // 1. Try local check first
            var user = database.userDao().getUserByEmail(email)
            var isValid = (user != null && user.password == password)

            // 2. If not valid locally, try cloud check
            if (!isValid) {
                val cloudData = FirebaseSyncManager.fetchUserDataFromCloud(getApplication(), email)
                if (cloudData != null && cloudData.user.password == password) {
                    user = cloudData.user
                    isValid = true
                    // Cache user locally
                    database.userDao().insertUser(user)
                    // Restore balances
                    cloudData.balances.forEach { (symbol, amount) ->
                        repository.setBalance(symbol, amount)
                    }
                }
            }

            if (!isValid || user == null) {
                launch(Dispatchers.Main) {
                    onError("Invalid email or password")
                }
            } else {
                // Persist session
                val prefs = getApplication<Application>().getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
                prefs.edit().putString("logged_in_user_email", user.email).apply()

                launch(Dispatchers.Main) {
                    _currentUser.value = user
                    setupCloudSync(getApplication(), user)
                    // Sync up local on login
                    triggerCloudBackup()
                    onSuccess()
                }
            }
        }
    }

    fun logoutUser() {
        stopCloudSync()
        val prefs = getApplication<Application>().getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
        prefs.edit().remove("logged_in_user_email").apply()
        _currentUser.value = null
    }
}
