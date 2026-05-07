package dk.huskplakaten.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [PlakatEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun plakatDao(): PlakatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "husk-plakaten-db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plakater ADD COLUMN removedAtMillis INTEGER")
                db.execSQL("ALTER TABLE plakater ADD COLUMN removedLatitude REAL")
                db.execSQL("ALTER TABLE plakater ADD COLUMN removedLongitude REAL")
                db.execSQL("ALTER TABLE plakater ADD COLUMN removalImageJpeg BLOB")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plakater ADD COLUMN ownerUserId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
