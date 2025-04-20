package com.survivalcomunicator.app.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migración que añade columnas necesarias para la verificación de usuarios
 * - verification_in_progress: Indica si hay un proceso de verificación en curso
 * - verified: Indica si el usuario está verificado
 * - public_key: Clave pública del usuario para verificación criptográfica
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Verificar primero si las columnas ya existen para evitar errores
        val cursor = database.query("PRAGMA table_info(users)")
        val columnNames = mutableListOf<String>()
        
        while (cursor.moveToNext()) {
            columnNames.add(cursor.getString(cursor.getColumnIndex("name")))
        }
        cursor.close()
        
        // Añadir columna public_key si no existe
        if (!columnNames.contains("public_key")) {
            database.execSQL("ALTER TABLE users ADD COLUMN public_key TEXT DEFAULT ''")
        }
        
        // Añadir columna verification_in_progress si no existe
        if (!columnNames.contains("verification_in_progress")) {
            database.execSQL("ALTER TABLE users ADD COLUMN verification_in_progress INTEGER DEFAULT 0 NOT NULL")
        }
        
        // Añadir columna verified si no existe
        if (!columnNames.contains("verified")) {
            database.execSQL("ALTER TABLE users ADD COLUMN verified INTEGER DEFAULT 0 NOT NULL")
        }
    }
}

/**
 * Función de utilidad para añadir la migración a tu AppDatabase
 */
fun getMigrations(): Array<Migration> {
    return arrayOf(MIGRATION_1_2)
}
