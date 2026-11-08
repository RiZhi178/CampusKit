package qdu.campus.di

import android.content.Context
import androidx.room.Room
import qdu.campus.platform.network.CookieBridge
import qdu.campus.platform.network.HttpClientFactory
import qdu.campus.platform.security.CredentialStore
import qdu.campus.platform.security.EncryptedCredentialStore
import qdu.campus.data.local.db.CalendarDao
import qdu.campus.data.local.db.CampusDatabase
import qdu.campus.data.local.db.DormBuildingDao
import qdu.campus.data.local.db.MIGRATION_1_2
import qdu.campus.data.local.db.MIGRATION_2_3
import qdu.campus.data.local.db.MIGRATION_3_4
import qdu.campus.data.local.db.MIGRATION_4_5
import qdu.campus.data.local.db.MIGRATION_5_6
import qdu.campus.data.local.db.NoticeDao
import qdu.campus.data.local.db.SyncDao
import qdu.campus.data.local.db.TimetableDao
import qdu.campus.data.local.db.UgTimetableDao
import qdu.campus.data.local.preferences.DataStoreSettingsRepository
import qdu.campus.data.remote.graduate.NoticeParser
import qdu.campus.data.remote.jwc.JwcNoticeParser
import qdu.campus.data.repository.EhallRepositoryImpl
import qdu.campus.data.repository.LibraryRepositoryImpl
import qdu.campus.data.repository.NoticeRepositoryImpl
import qdu.campus.data.repository.ProfileRepositoryImpl
import qdu.campus.data.repository.BaoxiuRepositoryImpl
import qdu.campus.data.repository.TimetableSourceRouter
import qdu.campus.data.repository.SeatRepositoryImpl
import qdu.campus.data.repository.SessionRepositoryImpl
import qdu.campus.data.repository.TimetableRepositoryImpl
import qdu.campus.data.repository.XgRepositoryImpl
import qdu.campus.data.repository.ZzmpRepositoryImpl
import qdu.campus.domain.repository.EhallRepository
import qdu.campus.domain.repository.LibraryRepository
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.domain.repository.ProfileRepository
import qdu.campus.domain.repository.BaoxiuRepository
import qdu.campus.domain.repository.SeatRepository
import qdu.campus.domain.repository.SessionRepository
import qdu.campus.domain.repository.SettingsRepository
import qdu.campus.domain.repository.TimetableRepository
import qdu.campus.domain.repository.XgRepository
import qdu.campus.domain.repository.ZzmpRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CampusDatabase =
        Room.databaseBuilder(context, CampusDatabase::class.java, CampusDatabase.NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTimetableDao(db: CampusDatabase): TimetableDao = db.timetableDao()

    @Provides
    fun provideNoticeDao(db: CampusDatabase): NoticeDao = db.noticeDao()

    @Provides
    fun provideSyncDao(db: CampusDatabase): SyncDao = db.syncDao()

    @Provides
    fun provideUgTimetableDao(db: CampusDatabase): UgTimetableDao = db.ugTimetableDao()

    @Provides
    fun provideDormBuildingDao(db: CampusDatabase): DormBuildingDao = db.dormBuildingDao()

    @Provides
    fun provideCalendarDao(db: CampusDatabase): CalendarDao = db.calendarDao()

    @Provides
    @Singleton
    fun provideNoticeParser(): NoticeParser = NoticeParser()

    @Provides
    fun provideJwcNoticeParser(): JwcNoticeParser = JwcNoticeParser()

    @Provides
    @Singleton
    fun provideHttpClientFactory(cookieBridge: CookieBridge) =
        HttpClientFactory(cookieBridge)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingModule {

    @Binds
    @Singleton
    abstract fun bindCredentialStore(impl: EncryptedCredentialStore): CredentialStore

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: SessionRepositoryImpl): SessionRepository

    @Binds
    @Singleton
    abstract fun bindTimetableRepository(impl: TimetableSourceRouter): TimetableRepository

    @Binds
    @Singleton
    abstract fun bindNoticeRepository(impl: NoticeRepositoryImpl): NoticeRepository

    @Binds
    @Singleton
    abstract fun bindSeatRepository(impl: SeatRepositoryImpl): SeatRepository

    @Binds
    @Singleton
    abstract fun bindBaoxiuRepository(impl: BaoxiuRepositoryImpl): BaoxiuRepository

    @Binds
    @Singleton
    abstract fun bindEhallRepository(impl: EhallRepositoryImpl): EhallRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindZzmpRepository(impl: ZzmpRepositoryImpl): ZzmpRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindXgRepository(impl: XgRepositoryImpl): XgRepository
}
