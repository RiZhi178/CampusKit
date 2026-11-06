package qdu.campus.domain.model

/** 色板生成变体。石墨的种子彩度只有 9，走 TonalSpot 会被抬到 36 变成蓝色，必须单独用 Neutral。 */
enum class PaletteVariant { TONAL_SPOT, NEUTRAL }

/**
 * 四套配色方案，只决定色相。名称与种子固定，不提供自由调色与壁纸取色。
 * 种子是唯一的颜色常量来源，业务组件只读生成后的语义角色。
 *
 * 删掉的旧方案（校徽蓝、青碧、墨绿、琥珀、绛红、靛紫）没有迁移映射：
 * `fromName` 认不出旧名字时回落到靛蓝，也就是新的默认方案。
 */
enum class AppearanceScheme(val label: String, val seed: Long, val variant: PaletteVariant) {
    /** 校徽主色 #3E3573 的 HCT 色相是 292.6，偏紫；往蓝侧收到 282.8 才是靛蓝。 */
    Indigo("靛蓝", 0xFF3A3F86L, PaletteVariant.TONAL_SPOT),

    /** 色相 126.9，落在黄色（110）与绿色（142）之间，再偏一点就是青柠。 */
    AppleGreen("苹果青", 0xFF7E9C3FL, PaletteVariant.TONAL_SPOT),

    Rose("玫紫", 0xFF963D78L, PaletteVariant.TONAL_SPOT),

    Graphite("石墨", 0xFF5A5F67L, PaletteVariant.NEUTRAL),
    ;

    companion object {
        fun fromName(name: String?): AppearanceScheme? = entries.firstOrNull { it.name == name }
    }
}

/** 明暗只决定 tone 朝向，与方案互相独立，两者组合出 12 种外观。 */
enum class AppearanceMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色"),
    ;

    companion object {
        fun fromName(name: String?): AppearanceMode? = entries.firstOrNull { it.name == name }
    }
}
