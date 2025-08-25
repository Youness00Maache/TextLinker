package com.xfire.textlinker

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NoteEntity::class], version = 2)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var instance: NoteDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN from_server INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): NoteDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notes_db"
                ).addMigrations(MIGRATION_1_2)
                 .addCallback(object : RoomDatabase.Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Verify the schema after database is opened
                        verifySchema(db)
                    }
                })
                 .build().also { instance = it }
            }

        private fun verifySchema(db: SupportSQLiteDatabase) {
            try {
                // Check if the from_server column exists and has the correct default value
                val cursor = db.query("PRAGMA table_info(notes)")
                var hasFromServerColumn = false
                var hasCorrectDefault = false
                
                while (cursor.moveToNext()) {
                    val nameIndex = cursor.getColumnIndex("name")
                    val dfltValueIndex = cursor.getColumnIndex("dflt_value")
                    
                    if (nameIndex >= 0 && cursor.getString(nameIndex) == "from_server") {
                        hasFromServerColumn = true
                        if (dfltValueIndex >= 0) {
                            val defaultValue = cursor.getString(dfltValueIndex)
                            hasCorrectDefault = defaultValue == "0"
                            Log.d("NoteDatabase", "Found from_server column with default value: $defaultValue")
                        }
                    }
                }
                cursor.close()
                
                if (!hasFromServerColumn) {
                    Log.e("NoteDatabase", "ERROR: from_server column is missing from the notes table!")
                } else if (!hasCorrectDefault) {
                    Log.e("NoteDatabase", "ERROR: from_server column has incorrect default value!")
                } else {
                    Log.d("NoteDatabase", "Schema verification passed: from_server column exists with default 0")
                }
                
                // Log all notes to verify their from_server values
                val notesCursor = db.query("SELECT id, title, from_server FROM notes")
                Log.d("NoteDatabase", "Found ${notesCursor.count} notes in the database:")
                
                while (notesCursor.moveToNext()) {
                    val id = notesCursor.getLong(notesCursor.getColumnIndexOrThrow("id"))
                    val title = notesCursor.getString(notesCursor.getColumnIndexOrThrow("title"))
                    val fromServer = notesCursor.getInt(notesCursor.getColumnIndexOrThrow("from_server")) != 0
                    Log.d("NoteDatabase", "Note id=$id, title='${title.take(20)}...', fromServer=$fromServer")
                }
                notesCursor.close()
                
            } catch (e: Exception) {
                Log.e("NoteDatabase", "Error verifying schema", e)
            }
        }
    }
}
