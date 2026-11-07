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
        UgTimetableEntryEntity::class,
        DormBuildingEntity::class,
        CalendarDayEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class CampusDatabase : RoomDatabase() {
    abstract fun timetableDao(): TimetableDao
    abstract fun ugTimetableDao(): UgTimetableDao
    abstract fun dormBuildingDao(): DormBuildingDao
    abstract fun calendarDao(): CalendarDao
    abstract fun noticeDao(): NoticeDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "campus.db"
    }
}
