package com.example.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.core.data.model.IntruderEntity
import com.example.core.data.model.LockedAppEntity
import com.example.core.data.model.ProfileEntity
import com.example.core.data.model.ScheduleEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        LockedAppEntity::class,
        ProfileEntity::class,
        ScheduleEntity::class,
        IntruderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TaalaDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun profileDao(): ProfileDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun intruderDao(): IntruderDao

    companion object {
        @Volatile
        private var INSTANCE: TaalaDatabase? = null

        fun getInstance(context: Context): TaalaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaalaDatabase::class.java,
                    "taala_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate default profiles (Finance, Social, Work)
                            CoroutineScope(Dispatchers.IO).launch {
                                getInstance(context).profileDao().insertDefaultProfiles(
                                    listOf(
                                        ProfileEntity(
                                            id = "finance",
                                            name = "Finance",
                                            description = "Banking, Wallets, UPI, Crypto",
                                            colorHex = "#10B981", // Emerald
                                            iconName = "savings",
                                            relockDelaySeconds = 0, // Immediately
                                            isEnabled = true,
                                            isBuiltIn = true
                                        ),
                                        ProfileEntity(
                                            id = "social",
                                            name = "Social & Chat",
                                            description = "Messaging, Photos, Social Networks",
                                            colorHex = "#3B82F6", // Blue
                                            iconName = "chat",
                                            relockDelaySeconds = 30, // 30 seconds
                                            isEnabled = true,
                                            isBuiltIn = true
                                        ),
                                        ProfileEntity(
                                            id = "work",
                                            name = "Work & Private",
                                            description = "Documents, Email, Confidential tools",
                                            colorHex = "#8B5CF6", // Purple
                                            iconName = "business",
                                            relockDelaySeconds = 60, // 1 minute
                                            isEnabled = true,
                                            isBuiltIn = true
                                        )
                                    )
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
