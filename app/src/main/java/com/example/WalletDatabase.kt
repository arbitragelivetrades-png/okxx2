package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
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
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User)
}

@Database(entities = [CoinBalance::class, User::class], version = 2, exportSchema = false)
abstract class WalletDatabase : RoomDatabase() {
    abstract fun coinBalanceDao(): CoinBalanceDao
    abstract fun userDao(): UserDao

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

class WalletRepository(private val dao: CoinBalanceDao) {
    val allBalances: Flow<List<CoinBalance>> = dao.getAllBalancesFlow()

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
}
