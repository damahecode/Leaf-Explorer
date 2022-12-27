package com.leaf.explorer.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.leaf.explorer.database.*
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RoomModule {
    @Singleton
    @Provides
    fun provideRoomDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "Main2.db")
            .build()
    }

    @Provides
    fun provideFavFolderDao(appDatabase: AppDatabase): FavFolderDao {
        return appDatabase.favFolderDao()
    }

    @Provides
    fun provideWebTransferDao(appDatabase: AppDatabase): WebTransferDao {
        return appDatabase.webTransferDao()
    }
}