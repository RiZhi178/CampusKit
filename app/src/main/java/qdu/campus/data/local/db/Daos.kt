package qdu.campus.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    fun observeCourses(semesterId: String): Flow<List<CourseEntity>>

    @Query("SELECT * FROM sections WHERE termId = :termId ORDER BY sectionIndex")
    fun observeSections(termId: String): Flow<List<SectionEntity>>

    @Query("SELECT * FROM no_schedule_courses WHERE semesterId = :semesterId")
    fun observeNoSchedule(semesterId: String): Flow<List<NoScheduleCourseEntity>>

    @Query("SELECT DISTINCT semesterId FROM courses ORDER BY semesterId DESC LIMIT 1")
    fun observeLatestSemester(): Flow<String?>

    @Query("SELECT * FROM courses WHERE semesterId = :semesterId")
    suspend fun loadCourses(semesterId: String): List<CourseEntity>

    @Query("DELETE FROM courses WHERE semesterId = :semesterId")
    suspend fun deleteCourses(semesterId: String)

    @Query("DELETE FROM no_schedule_courses WHERE semesterId = :semesterId")
    suspend fun deleteNoSchedule(semesterId: String)

    @Query("DELETE FROM sections WHERE termId = :termId")
    suspend fun deleteSections(termId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNoSchedule(items: List<NoScheduleCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSections(sections: List<SectionEntity>)

    @Upsert
    suspend fun upsertTerm(term: TermEntity)

    @Query("SELECT * FROM terms WHERE id = :termId")
    suspend fun loadTerm(termId: String): TermEntity?

    @Query("SELECT * FROM terms WHERE id = :termId")
    fun observeTerm(termId: String): Flow<TermEntity?>

    @Query("DELETE FROM courses")
    suspend fun clearCourses()

    @Query("DELETE FROM no_schedule_courses")
    suspend fun clearNoScheduleAll()

    @Query("DELETE FROM sections")
    suspend fun clearSectionsAll()

    @Query("DELETE FROM terms")
    suspend fun clearTerms()

    /**
     * 全量覆盖：删除该学期旧记录后整体写入，放在同一事务里，
     * 避免刷新过程中页面读到半份数据。
     */
    @Transaction
    suspend fun replaceTimetable(
        semesterId: String,
        term: TermEntity,
        courses: List<CourseEntity>,
        noSchedule: List<NoScheduleCourseEntity>,
        sections: List<SectionEntity>,
    ) {
        upsertTerm(term)
        deleteCourses(semesterId)
        deleteNoSchedule(semesterId)
        deleteSections(semesterId)
        insertCourses(courses)
        insertNoSchedule(noSchedule)
        insertSections(sections)
    }
}

@Dao
interface NoticeDao {

    @Query("SELECT * FROM notices ORDER BY date DESC, title ASC")
    fun observeAll(): Flow<List<NoticeEntity>>

    @Query("SELECT fingerprint FROM notices")
    suspend fun existingFingerprints(): List<String>

    @Query("SELECT DISTINCT source FROM notices")
    suspend fun existingSources(): List<String>

    @Query("SELECT fingerprint FROM notices WHERE read = 1")
    suspend fun readFingerprints(): List<String>

    @Upsert
    suspend fun upsertAll(items: List<NoticeEntity>)

    @Query("UPDATE notices SET read = 1 WHERE fingerprint = :fingerprint")
    suspend fun markRead(fingerprint: String)

    @Query("DELETE FROM notices")
    suspend fun clear()
}

@Dao
interface SyncDao {

    @Upsert
    suspend fun upsert(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE source = :source")
    fun observe(source: String): Flow<SyncMetadataEntity?>

    @Query("SELECT * FROM sync_metadata")
    fun observeAll(): Flow<List<SyncMetadataEntity>>

    @Query("DELETE FROM sync_metadata")
    suspend fun clear()
}

