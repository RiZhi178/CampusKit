package qdu.campus.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TermEntity::class,
        SectionEntity::class,
        CourseEntity::class,
        NoScheduleCourseEntity::class,
        NoticeEntity::class,
        SyncMetadataEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun noticeDao(): NoticeDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "campus.db"
    }
}
