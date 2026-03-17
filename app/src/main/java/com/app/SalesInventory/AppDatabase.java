package com.app.SalesInventory;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

// CHANGED: version bumped to 14 for the automated reorder point variables
@Database(entities = {ProductEntity.class, SalesOrderEntity.class, SalesOrderItemEntity.class}, version = 15, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ProductDao productDao();
    public abstract SalesDao salesDao();

    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN floorLevel INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN preOrderLevel INTEGER NOT NULL DEFAULT 0");
        }
    };

    // NEW: Migration 13 to 14 to add automation variables
    private static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE products ADD COLUMN leadTimeDays INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE products ADD COLUMN safetyStock REAL NOT NULL DEFAULT 0.0");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "sales_inventory_db"
                            )
                            // Add MIGRATION_13_14 to the list
                            .addMigrations(MIGRATION_6_7, MIGRATION_12_13, MIGRATION_13_14)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void closeDatabase() {
        if (INSTANCE != null) {
            if (INSTANCE.isOpen()) {
                INSTANCE.close();
            }
            INSTANCE = null;
        }
    }

    public static void resetInstance() {
        INSTANCE = null;
    }
}