package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "coin_balances")
data class CoinBalance(
    @PrimaryKey val symbol: String,
    val amount: Double
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val username: String,
    val password: String
)

fun User?.isAdmin(): Boolean {
    if (this == null) return false
    val lower = email.lowercase().trim()
    return lower == "arbitragelivetrades@gmail.com" || lower.startsWith("admin") || lower.contains("admin")
}

@Dao
interface CoinBalanceDao {
    @Query("SELECT * FROM coin_balances")
    fun getAllBalancesFlow(): Flow<List<CoinBalance>>

    @Query("SELECT * FROM coin_balances")
    suspend fun getAllBalances(): List<CoinBalance>

    @Query("SELECT * FROM coin_balances WHERE symbol = :symbol LIMIT 1")
    suspend fun getBalanceBySymbol(symbol: String): CoinBalance?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(balance: CoinBalance)

    @Query("DELETE FROM coin_balances WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE FROM users WHERE LOWER(email) = LOWER(:email)")
    suspend fun deleteUserByEmail(email: String)

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val symbol: String,
    val amount: Double,
    val isPositive: Boolean,
    val status: String,
    val timestampMs: Long,
    val formattedDate: String,
    val formattedDetailTime: String,
    val usdValue: String,
    val address: String,
    val priceInfo: String,
    val network: String,
    val networkFee: String,
    val txId: String,
    val referenceNo: String,
    val monthYear: String
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMs DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMs DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)
}

@Database(entities = [CoinBalance::class, User::class, TransactionEntity::class], version = 3, exportSchema = false)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun coinBalanceDao(): CoinBalanceDao
    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: WalletDatabase? = null

        fun getDatabase(context: Context): WalletDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    WalletDatabase::class.java,
                    "wallet_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class WalletRepository(
    private val dao: CoinBalanceDao,
    private val transactionDao: TransactionDao
) {
    val allBalances: Flow<List<CoinBalance>> = dao.getAllBalancesFlow()
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsFlow()

    suspend fun getBalance(symbol: String): Double {
        return dao.getBalanceBySymbol(symbol.uppercase())?.amount ?: 0.0
    }

    suspend fun addBalance(symbol: String, amount: Double) {
        val sym = symbol.uppercase()
        val current = dao.getBalanceBySymbol(sym)?.amount ?: 0.0
        dao.insertOrUpdate(CoinBalance(sym, current + amount))
    }

    suspend fun withdrawBalance(symbol: String, amount: Double): Boolean {
        val sym = symbol.uppercase()
        val current = dao.getBalanceBySymbol(sym)?.amount ?: 0.0
        val newAmount = (current - amount).coerceAtLeast(0.0)
        dao.insertOrUpdate(CoinBalance(sym, newAmount))
        return true
    }

    suspend fun setBalance(symbol: String, amount: Double) {
        dao.insertOrUpdate(CoinBalance(symbol.uppercase(), amount))
    }

    suspend fun addTransaction(transaction: TransactionEntity) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun seedTransactions(list: List<TransactionEntity>) {
        transactionDao.insertAll(list)
    }
}
