package qdu.campus.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2 只新增微信消息表，旧数据原样保留。 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `wechat_messages` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `dedupeKey` TEXT NOT NULL,
                `conversation` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `postedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_wechat_messages_dedupeKey` " +
                "ON `wechat_messages` (`dedupeKey`)",
        )
    }
}

/** v4 通知表加来源列，接入教务处教务通知；历史行全部归研究生院，已读状态原样保留。 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notices` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'grad_notice'")
    }
}

/** v3 移除微信消息镜像，表与索引一并丢掉，其余数据仍在。 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `wechat_messages`")
    }
}

/**
 * v5 新增三张种子表：本科班级课表、电费楼栋字典、校历调停课规则。
 * 全部是空表起步，内容由启动时的资产种子填充；旧数据（terms / sections /
 * courses / notices 等）原样保留。
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ug_timetable_entries` (
                `id` TEXT NOT NULL,
                `semesterId` TEXT NOT NULL,
                `className` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `teachers` TEXT NOT NULL,
                `dept` TEXT NOT NULL,
                `campus` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `day` INTEGER NOT NULL,
                `periods` TEXT NOT NULL,
                `weeks` TEXT NOT NULL,
                `room` TEXT NOT NULL,
                `credits` REAL NOT NULL,
                `hours` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_ug_timetable_entries_semesterId_className` " +
                "ON `ug_timetable_entries` (`semesterId`, `className`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dorm_buildings` (
                `buildingId` TEXT NOT NULL,
                `campusId` TEXT NOT NULL,
                `campus` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `code` TEXT,
                PRIMARY KEY(`buildingId`)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `calendar_days` (
                `date` TEXT NOT NULL,
                `academicYear` TEXT NOT NULL,
                `kind` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `followsDate` TEXT,
                PRIMARY KEY(`date`)
            )
            """.trimIndent(),
        )
    }
}
