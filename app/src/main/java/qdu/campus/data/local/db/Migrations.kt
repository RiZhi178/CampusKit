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
