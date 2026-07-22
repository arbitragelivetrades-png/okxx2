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
    private val repository = WalletRepository(database.coinBalanceDao(), database.transactionDao())

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

    val transactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
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
                        // Restore their cloud transactions/withdrawals
                        if (cloudData.transactions.isNotEmpty()) {
                            repository.seedTransactions(cloudData.transactions)
                        }
                        android.util.Log.d("WalletViewModel", "User $savedEmail restored successfully from Cloud Firestore with ${cloudData.transactions.size} transactions.")
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
                        // Restore or sync transactions
                        if (cloudData.transactions.isNotEmpty()) {
                            repository.seedTransactions(cloudData.transactions)
                        }
                        android.util.Log.d("WalletViewModel", "User $savedEmail, balances, and transactions synced successfully on startup.")
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

        // Seed initial transactions matching screenshot
        viewModelScope.launch(Dispatchers.IO) {
            val currentTxList = database.transactionDao().getAllTransactions()
            if (currentTxList.isEmpty()) {
                val seedList = listOf(
                    TransactionEntity(
                        id = "tx_1001",
                        type = "Withdrawal",
                        symbol = "LTC",
                        amount = 0.17036216,
                        isPositive = false,
                        status = "Completed",
                        timestampMs = 1780697640000L,
                        formattedDate = "Jun 5, 2026, 22:14",
                        formattedDetailTime = "Jun 5, 2026, 10:14 PM",
                        usdValue = "~$7.95",
                        address = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
                        priceInfo = "$46.95/LTC",
                        network = "Litecoin",
                        networkFee = "0.001 LTC",
                        txId = "bdf3a8e91c724f128a30192c54bd",
                        referenceNo = "406122144",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1002",
                        type = "Received from trading account",
                        symbol = "LTC",
                        amount = 0.17036216,
                        isPositive = true,
                        status = "Completed",
                        timestampMs = 1780697640000L,
                        formattedDate = "Jun 5, 2026, 22:14",
                        formattedDetailTime = "Jun 5, 2026, 10:14 PM",
                        usdValue = "~$7.95",
                        address = "Internal Transfer",
                        priceInfo = "$46.95/LTC",
                        network = "Litecoin",
                        networkFee = "0.000 LTC",
                        txId = "f72b1093a4c01928374a123bc",
                        referenceNo = "406122145",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1003",
                        type = "Transferred to trading account",
                        symbol = "USDT",
                        amount = 2.30999999,
                        isPositive = false,
                        status = "Completed",
                        timestampMs = 1780697460000L,
                        formattedDate = "Jun 5, 2026, 22:11",
                        formattedDetailTime = "Jun 5, 2026, 10:11 PM",
                        usdValue = "~$2.31",
                        address = "Internal Transfer",
                        priceInfo = "$1.00/USDT",
                        network = "TRON (TRC20)",
                        networkFee = "0.000 USDT",
                        txId = "a92b103e9182312039281a123",
                        referenceNo = "406122110",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1004",
                        type = "Fulfill an order",
                        symbol = "USDT",
                        amount = 2.31,
                        isPositive = true,
                        status = "Completed",
                        timestampMs = 1780697400000L,
                        formattedDate = "Jun 5, 2026, 22:10",
                        formattedDetailTime = "Jun 5, 2026, 10:10 PM",
                        usdValue = "~$2.31",
                        address = "Spot Order #8821902",
                        priceInfo = "$1.00/USDT",
                        network = "Internal",
                        networkFee = "0.000 USDT",
                        txId = "e812903bc1829381023912a",
                        referenceNo = "406122100",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1005",
                        type = "Transferred to trading account",
                        symbol = "USDT",
                        amount = 5.00999999,
                        isPositive = false,
                        status = "Completed",
                        timestampMs = 1780695900000L,
                        formattedDate = "Jun 5, 2026, 21:45",
                        formattedDetailTime = "Jun 5, 2026, 9:45 PM",
                        usdValue = "~$5.01",
                        address = "Internal Transfer",
                        priceInfo = "$1.00/USDT",
                        network = "TRON (TRC20)",
                        networkFee = "0.000 USDT",
                        txId = "c912039120839182301923",
                        referenceNo = "406122045",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1006",
                        type = "Fulfill an order",
                        symbol = "USDT",
                        amount = 5.01,
                        isPositive = true,
                        status = "Completed",
                        timestampMs = 1780695540000L,
                        formattedDate = "Jun 5, 2026, 21:39",
                        formattedDetailTime = "Jun 5, 2026, 9:39 PM",
                        usdValue = "~$5.01",
                        address = "Spot Order #8821811",
                        priceInfo = "$1.00/USDT",
                        network = "Internal",
                        networkFee = "0.000 USDT",
                        txId = "d819203819023812039182",
                        referenceNo = "406121939",
                        monthYear = "Jun 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1007",
                        type = "Place an order",
                        symbol = "USDT",
                        amount = 87.14,
                        isPositive = false,
                        status = "Completed",
                        timestampMs = 1779244620000L,
                        formattedDate = "May 20, 2026, 02:37",
                        formattedDetailTime = "May 20, 2026, 2:37 AM",
                        usdValue = "~$87.14",
                        address = "Spot Order #7712390",
                        priceInfo = "$1.00/USDT",
                        network = "Internal",
                        networkFee = "0.000 USDT",
                        txId = "b81920381203819203819",
                        referenceNo = "405200237",
                        monthYear = "May 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1008",
                        type = "Received from trading account",
                        symbol = "USDT",
                        amount = 87.14,
                        isPositive = true,
                        status = "Completed",
                        timestampMs = 1779244620000L,
                        formattedDate = "May 20, 2026, 02:37",
                        formattedDetailTime = "May 20, 2026, 2:37 AM",
                        usdValue = "~$87.14",
                        address = "Internal Transfer",
                        priceInfo = "$1.00/USDT",
                        network = "TRON (TRC20)",
                        networkFee = "0.000 USDT",
                        txId = "a18920381203819203812",
                        referenceNo = "405200237",
                        monthYear = "May 2026"
                    ),
                    TransactionEntity(
                        id = "tx_1009",
                        type = "Transferred to trading account",
                        symbol = "USDT",
                        amount = 87.14848899,
                        isPositive = false,
                        status = "Completed",
                        timestampMs = 1779244080000L,
                        formattedDate = "May 20, 2026, 02:28",
                        formattedDetailTime = "May 20, 2026, 2:28 AM",
                        usdValue = "~$87.15",
                        address = "Internal Transfer",
                        priceInfo = "$1.00/USDT",
                        network = "TRON (TRC20)",
                        networkFee = "0.000 USDT",
                        txId = "f18920381203819203811",
                        referenceNo = "405200228",
                        monthYear = "May 2026"
                    )
                )
                repository.seedTransactions(seedList)
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
                val allTransactions = database.transactionDao().getAllTransactions()

                FirebaseSyncManager.syncUserToCloud(
                    context = getApplication(),
                    user = user,
                    balances = currentBalances,
                    transactions = allTransactions,
                    lastUpdatedTimestamp = now
                )
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
            recordTransaction(
                type = "Deposit",
                symbol = symbol,
                amount = amount,
                isPositive = true,
                address = "Internal Deposit",
                network = "Internal",
                status = "Completed"
            )
            // Backup to cloud with the updated balance directly
            triggerCloudBackup()
        }
    }

    fun withdrawBalance(
        symbol: String,
        amount: Double,
        address: String = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
        network: String = "Litecoin",
        status: String = "Completed",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isOnline.value) {
            viewModelScope.launch(Dispatchers.Main) {
                onError("Restricted: Device is offline")
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val success = repository.withdrawBalance(symbol.uppercase(), amount)
            if (success) {
                recordTransaction(
                    type = "Withdrawal",
                    symbol = symbol,
                    amount = amount,
                    isPositive = false,
                    address = if (address.isNotBlank()) address else "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
                    network = if (network.isNotBlank()) network else symbol.uppercase(),
                    status = status
                )
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
                    // Restore transactions
                    if (cloudData.transactions.isNotEmpty()) {
                        repository.seedTransactions(cloudData.transactions)
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

    fun recordTransaction(
        type: String,
        symbol: String,
        amount: Double,
        isPositive: Boolean,
        address: String = "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
        network: String = "Litecoin",
        status: String = "Completed"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val sdfDate = java.text.SimpleDateFormat("MMM d, yyyy, HH:mm", java.util.Locale.US)
            val sdfDetail = java.text.SimpleDateFormat("MMM d, yyyy, h:mm a", java.util.Locale.US)
            val sdfMonth = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US)
            val dateObj = java.util.Date(now)
            val formattedDate = sdfDate.format(dateObj)
            val formattedDetailTime = sdfDetail.format(dateObj)
            val monthYear = sdfMonth.format(dateObj)
            val price = _prices.value[symbol.uppercase()] ?: 1.0
            val usdVal = String.format("~$%.2f", amount * price)
            val randomTxId = java.util.UUID.randomUUID().toString().replace("-", "").take(24)
            val randomRef = (100000000..999999999).random().toString()

            val tx = TransactionEntity(
                id = "tx_${now}_${(100..999).random()}",
                type = type,
                symbol = symbol.uppercase(),
                amount = amount,
                isPositive = isPositive,
                status = status,
                timestampMs = now,
                formattedDate = formattedDate,
                formattedDetailTime = formattedDetailTime,
                usdValue = usdVal,
                address = if (address.isNotBlank()) address else "3NpoyjVA9faxhzSj9LXCo93FB9VyTooTAdBeM83aGkYq",
                priceInfo = String.format("$%.2f/%s", price, symbol.uppercase()),
                network = network,
                networkFee = "0.001 ${symbol.uppercase()}",
                txId = randomTxId,
                referenceNo = randomRef,
                monthYear = monthYear
            )
            repository.addTransaction(tx)
            triggerCloudBackup()
        }
    }
}
