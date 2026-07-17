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
import org.json.JSONObject
import kotlin.random.Random

class WalletViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WalletDatabase.getDatabase(application)
    private val repository = WalletRepository(database.coinBalanceDao())

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
            "BTC" to 58320.00,
            "ETH" to 3120.00,
            "BNB" to 550.00,
            "SOL" to 77.47, // Initial default matching user's exact example: 2 SOL = 154.94 USD (77.47 per SOL)
            "TRX" to 0.134,
            "1INCH" to 0.41,
            "XAUT" to 2350.00
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

    private val client = OkHttpClient()

    init {
        // Load persisted user session if any
        viewModelScope.launch(Dispatchers.IO) {
            val prefs = application.getSharedPreferences("okx_settings", Context.MODE_PRIVATE)
            val savedEmail = prefs.getString("logged_in_user_email", null)
            if (savedEmail != null) {
                val user = database.userDao().getUserByEmail(savedEmail)
                if (user != null) {
                    _currentUser.value = user
                    // Start cloud sync listener
                    launch(Dispatchers.Main) {
                        setupCloudSync(application, user)
                    }
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
            .url("https://min-api.cryptocompare.com/data/pricemulti?fsyms=BTC,ETH,BNB,SOL,USDT,TRX,1INCH,XAUT,USDG&tsyms=USD")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return
                val json = JSONObject(responseBody)
                val newPrices = _prices.value.toMutableMap()

                listOf("BTC", "ETH", "BNB", "SOL", "USDT", "TRX", "1INCH", "XAUT", "USDG").forEach { symbol ->
                    if (json.has(symbol)) {
                        val usdObj = json.getJSONObject(symbol)
                        if (usdObj.has("USD")) {
                            newPrices[symbol] = usdObj.getDouble("USD")
                        }
                    }
                }
                _prices.value = newPrices
            }
        }
    }

    fun setupCloudSync(context: Context, user: User) {
        if (FirebaseSyncManager.isConfigured(context)) {
            FirebaseSyncManager.startRealtimeSync(context, user.email) { updatedBalances ->
                viewModelScope.launch(Dispatchers.IO) {
                    updatedBalances.forEach { (symbol, amount) ->
                        repository.setBalance(symbol, amount)
                    }
                }
            }
        }
    }

    fun stopCloudSync() {
        FirebaseSyncManager.stopRealtimeSync()
    }

    fun triggerCloudBackup() {
        val user = _currentUser.value ?: return
        val currentBalances = coinBalances.value
        FirebaseSyncManager.syncUserToCloud(getApplication(), user, currentBalances)
    }

    fun addBalance(symbol: String, amount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBalance(symbol.uppercase(), amount)
            // Backup to cloud
            launch(Dispatchers.Main) {
                triggerCloudBackup()
            }
        }
    }

    fun withdrawBalance(symbol: String, amount: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.withdrawBalance(symbol.uppercase(), amount)
            launch(Dispatchers.Main) {
                if (success) {
                    triggerCloudBackup()
                    onSuccess()
                } else {
                    onError("Insufficient balance")
                }
            }
        }
    }

    fun registerUser(user: User, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.userDao().getUserByEmail(user.email)
            if (existing != null) {
                launch(Dispatchers.Main) {
                    onError("Email already registered")
                }
            } else {
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
    }

    fun loginUser(email: String, password: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val user = database.userDao().getUserByEmail(email)
            if (user == null || user.password != password) {
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
