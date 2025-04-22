package com.survivalcomunicator.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserEntity::class, MessageEntity::class], // Asegúrate de incluir todas tus entidades
    version = 2, // Incrementamos a versión 2
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao // Si tienes un DAO para mensajes

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "survival_comunicator.db"
                )
                    .addMigrations(*getMigrations()) // Añadimos las migraciones
                    .fallbackToDestructiveMigration() // Opcional: si la migración falla, recreará la base de datos
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return getInstance(context)
        }
    }
}