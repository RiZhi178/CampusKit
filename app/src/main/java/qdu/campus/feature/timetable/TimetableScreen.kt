package qdu.campus.feature.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.domain.model.ClassSection
import qdu.campus.domain.model.Course
import qdu.campus.domain.model.NoScheduleCourse
import qdu.campus.domain.model.SemesterOption
import qdu.campus.domain.model.TimetableLevel
import qdu.campus.domain.model.WeekSchedule
import qdu.campus.platform.common.Formats
import qdu.campus.platform.designsystem.LocalStatusColors
import qdu.campus.platform.designsystem.InlineNotice
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.courseSurface
import qdu.campus.platform.designsystem.listBottomGuard
import qdu.campus.platform.system.ExternalLauncher
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

private val TimeColumnWidth = 44.dp
private val CellMinHeight = 62.dp
private val SwipeThreshold = 64f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    onOpenLogin: () -> Unit,
    /** 打开本科教务系统（强智 jsxsd）自己的登录页，与统一认证是两套账号体系。 */
    onOpenStudentLogin: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var editSchedule by remember { mutableStateOf(false) }
    var pickClasses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = remember { ExternalLauncher(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表") },
                actions = {
                    IconButton(onClick = { editSchedule = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "学期与节次设置")
                    }
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新课表")
                    }
                },
            )
        },
        // 底部由外层图标底栏让出，这里只处理状态栏与左右。
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            WeekSwitcher(
                selected = state.selectedWeek,
                current = state.currentWeek,
                max = state.maxWeek,
                semester = state.semesterId,
                weekRange = state.weekRange,
                onPrevious = { viewModel.selectWeek(state.selectedWeek - 1) },
                onNext = { viewModel.selectWeek(state.selectedWeek + 1) },
                onToday = viewModel::backToCurrentWeek,
            )

            // 新学期资产就绪而旧勾选失效：一个都匹配不上才提示，部分缺失不给用户添堵。
            if (state.staleClasses) {
                InlineNotice(
                    text = "勾选的班级在新学期课程总表里找不到了——课表大概率已更新，重新选一次班",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    actionLabel = "重新选班",
                    onAction = { pickClasses = true },
                )
            }

            val message = state.message
            if (message != null) {
                // 会话失效只有重新登录能解决；其余失败点「知道了」就够
                InlineNotice(
                    text = message,
                    isError = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    actionLabel = if (state.loginRequired) "去登录" else "知道了",
                    onAction = {
                        if (state.loginRequired) {
                            viewModel.dismissMessage()
                            onOpenLogin()
                        } else {
                            viewModel.dismissMessage()
                        }
                    },
                )
            }

            // 数据来源警告的可关闭状态：本科课表只来自公开总表，这句话每次进页面都值得再看一眼，
            // 但同一次会话里关掉就不再弹。
            var dataNoticeVisible by rememberSaveable { mutableStateOf(true) }

            // 本科课表只来自公开总表，与研究生的「同步失败」用同一位置的同一横幅交代数据边界
            if (state.level == TimetableLevel.Undergraduate && dataNoticeVisible) {
                // 与研究生的同步失败横幅同一套样式与动作：红色警示 + 「去登录」。
                // 但去的是本科教务系统自己的登录页（强智 LDAP），不是统一认证——两套账号体系别混；
                // 这行提示是静态的，跳走再回来它还在，由学生登录后按需自行收起
                InlineNotice(
                    text = "课表数据来自教务处公开信息，如需准确课表请登录",
                    isError = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    actionLabel = "去登录",
                    onAction = onOpenStudentLogin,
                )
            }

            val schedule = state.schedule
            val empty = schedule == null || schedule.days.values.all { it.isEmpty() }
            if (!state.hasData && empty) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (state.level) {
                        // 这台机器上还没选过课表来源，先问一次；以后在「学期与节次」里还能改
                        null -> LevelChoiceBlock(
                            onGraduate = { viewModel.setLevel(TimetableLevel.Graduate) },
                            onUndergraduate = {
                                viewModel.setLevel(TimetableLevel.Undergraduate)
                                pickClasses = true
                            },
                        )

                        TimetableLevel.Undergraduate -> PlaceholderBlock(
                            title = if (state.ugClassCount == 0) "还没有勾选班级" else "本周没有课",
                            description = if (state.ugClassCount == 0) {
                                "本科课表来自教务处公开的课程总表，那是全校的课，勾出自己的班级才会排进周历。"
                            } else {
                                "课程总表里找不到这些班的排课，可能是班级名改了，重新勾一次。"
                            },
                            actionLabel = "选择我的班级",
                            onAction = { pickClasses = true },
                        )

                        TimetableLevel.Graduate -> PlaceholderBlock(
                            title = if (state.isRefreshing) "正在同步课表" else "还没有课表数据",
                            description = if (state.loginRequired) {
                                "课表来自研究生系统，它只认自己的登录，需要单独登一次。"
                            } else {
                                "课表来自研究生系统，同步完成后会显示在周历上。"
                            },
                            actionLabel = if (state.loginRequired) "登录研究生系统" else null,
                            onAction = if (state.loginRequired) onOpenLogin else null,
                        )
                    }
                }
            } else if (schedule != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = listBottomGuard()),
                ) {
                    // 左右滑动切周，与顶部箭头同一套动作；纵向滚动不受影响。
                    var dragX by remember { mutableFloatStateOf(0f) }
                    Column(
                        modifier = Modifier.pointerInput(state.selectedWeek, state.maxWeek) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (dragX > SwipeThreshold) {
                                        viewModel.selectWeek(state.selectedWeek - 1)
                                    } else if (dragX < -SwipeThreshold) {
                                        viewModel.selectWeek(state.selectedWeek + 1)
                                    }
                                    dragX = 0f
                                },
                                onDragCancel = { dragX = 0f },
                                onHorizontalDrag = { change, amount ->
                                    dragX += amount
                                    change.consume()
                                },
                            )
                        },
                    ) {
                        ScheduleGrid(
                            schedule = schedule,
                            today = state.today,
                            isCurrentWeek = state.selectedWeek == state.currentWeek,
                            liveCourseIds = state.liveCourseIds,
                            onCourseClick = viewModel::selectCourse,
                        )
                    }

                    if (state.weekNotes.isNotEmpty()) {
                        WeekNoteCard(state.weekNotes)
                    }

                    if (state.noSchedule.isNotEmpty()) {
                        NoScheduleSection(state.noSchedule)
                    }

                    FootLine(
                        updatedAt = state.updatedAt,
                        hasLiveCourse = state.liveCourseIds.isNotEmpty(),
                    )
                }
            }
        }
    }

    state.selectedCourse?.let { course ->
        CourseDetailDialog(
            course = course,
            onCopyRoom = { launcher.copyToClipboard(course.room, "教室") },
            onDismiss = { viewModel.selectCourse(null) },
        )
    }

    if (editSchedule) {
        ScheduleDialog(
            semesterId = state.semesterId,
            termStartDate = state.termStartDate,
            sections = state.baseSections,
            overrides = state.sectionOverrides,
            level = state.level,
            semesterOptions = state.semesterOptions,
            classCount = state.ugClassCount,
            onDismiss = { editSchedule = false },
            onPickClasses = {
                editSchedule = false
                pickClasses = true
            },
            onSave = { level, semester, start, overrides ->
                // 来源与学期、起始日一样，都是点「保存」才生效：取消时什么都不会发生
                viewModel.setLevel(level)
                viewModel.setSemester(semester)
                viewModel.setTermStartDate(start)
                viewModel.setSectionOverrides(overrides)
                editSchedule = false
                if (level == TimetableLevel.Undergraduate && state.ugClassCount == 0) {
                    // 刚切过来但还没勾过班，直接把勾选框打开，别让人再去找按钮
                    pickClasses = true
                }
            },
        )
    }

    if (pickClasses) {
        ClassPickerDialog(
            onDismiss = { pickClasses = false },
            onApplied = { pickClasses = false },
        )
    }
}

@Composable
private fun WeekSwitcher(
    selected: Int,
    current: Int,
    max: Int,
    semester: String,
    weekRange: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = selected > 1) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 $selected 周",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (selected == current) {
                    Text(
                        text = "本周",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            // 副行交代这一周是学期的哪一周、覆盖哪几天，切周时不至于对不上号
            val subtitle = listOf(semester, weekRange).filter { it.isNotBlank() }
                .joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected != current) {
            QduButton(
                text = "回到本周",
                kind = QduButtonKind.Ghost,
                small = true,
                onClick = onToday,
            )
        }
        IconButton(onClick = onNext, enabled = selected < max) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
        }
    }
}

/**
 * 七列等分占满屏宽，不再横向滚动。行高由课程块内容撑开，
 * 字体放大时整行一起变高，不会把文字裁掉。
 */
@Composable
private fun ScheduleGrid(
    schedule: WeekSchedule,
    today: LocalDate,
    isCurrentWeek: Boolean,
    liveCourseIds: Set<String>,
    onCourseClick: (Course) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val status = LocalStatusColors.current
    val todayDay = if (isCurrentWeek) today.dayOfWeek.value else -1
    val rows = schedule.sections.ifEmpty {
        listOf(ClassSection(1, "", "", "", "第一节"))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(TimeColumnWidth))
            (1..7).forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    // 列头统一高度：只显示「一二三四五六日」「今」「休」「调」，不显示日期数字
                    //（日期在周切换条的 mm.dd-mm.dd 区间里有，列头不再重复占一行）。
                    // 「今」优先级最高——今天即使是休/调日也只显示「今」，假期/调休信息进读屏描述，
                    // 视觉上由下方课程格自己交代（休=整天空、调=有课）。其他日期：校历调停课的
                    // 「休」（绿=休息）、「调」（黄=要上课）替换星期字装进实心圆点——状态色当底、
                    // 字按底色明度取黑或白；圆径与文字行高统一为 headerHeight，星期信息靠列位置
                    // 与读屏描述承载。
                    val note = schedule.dayNotes[day]
                    val headerHeight = with(LocalDensity.current) {
                        MaterialTheme.typography.labelMedium.lineHeight.toDp() * 1.5f
                    }
                    if (day == todayDay) {
                        Box(
                            modifier = Modifier.height(headerHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "今",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary,
                                modifier = Modifier.semantics {
                                    contentDescription = "今天，星期" +
                                        Formats.weekdayLabel(day).removePrefix("周") +
                                        when (note) {
                                            "休" -> "，假期休息"
                                            "调" -> "，调休上课"
                                            else -> ""
                                        }
                                },
                            )
                        }
                    } else if (note == null) {
                        Box(
                            modifier = Modifier.height(headerHeight),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = Formats.weekdayLabel(day).removePrefix("周"),
                                style = MaterialTheme.typography.labelMedium,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val noteColor = if (note == "休") status.success else status.warning
                        val onNote = if (noteColor.luminance() > 0.5f) Color.Black else Color.White
                        Box(
                            modifier = Modifier
                                .size(headerHeight)
                                .clip(CircleShape)
                                .background(noteColor)
                                .semantics {
                                    contentDescription =
                                        "星期" + Formats.weekdayLabel(day).removePrefix("周") +
                                            if (note == "休") "，假期休息" else "，调休上课"
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelMedium,
                                color = onNote,
                            )
                        }
                    }
                }
            }
        }

        rows.forEach { section ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = CellMinHeight),
            ) {
                Column(
                    modifier = Modifier
                        .width(TimeColumnWidth)
                        .fillMaxHeight()
                        .padding(start = 2.dp, top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = section.startTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                    Text(
                        text = section.endTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                (1..7).forEach { day ->
                    val courses = schedule.coursesOf(day).filter { section.contains(it.sections) }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(1.5.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (day == todayDay) {
                                    scheme.surfaceContainerHighest
                                } else {
                                    scheme.surfaceContainerLow
                                },
                            ),
                    ) {
                        if (courses.isNotEmpty()) {
                            // 同一格排了两门课是真实冲突，并排画出来：取第一门会把另一门吞掉
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                courses.forEach { course ->
                                    CourseBlock(
                                        course = course,
                                        live = course.id in liveCourseIds,
                                        onClick = { onCourseClick(course) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (schedule.days.values.all { it.isEmpty() }) {
            PlaceholderBlock(
                title = "第 ${schedule.week} 周没有课",
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

/** 单元格文字用 M3 label 字阶，行高由内容决定，字体放大时整行一起变高。 */
@Composable
private fun CourseBlock(
    course: Course,
    live: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val surface = courseSurface(course.colorKey)
    val shape = MaterialTheme.shapes.small
    Surface(
        shape = shape,
        color = surface.container,
        modifier = modifier
            .fillMaxSize()
            .then(
                // 正在上的那一节加描边，与页脚图例成对出现，不靠颜色单独说话
                if (live) {
                    Modifier.border(1.5.dp, scheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                onClick = onClick,
                role = Role.Button,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "${course.name}，${Formats.weekdayLabel(course.day)}" +
                    "${course.sectionLabel}，${course.room.ifBlank { "教室未标注" }}" +
                    if (live) "，正在上课" else ""
            },
    ) {
        Box {
            Column(modifier = Modifier.padding(3.dp)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = surface.onContainer,
                )
                if (course.room.isNotBlank()) {
                    Text(
                        text = course.room,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = surface.onContainer,
                    )
                }
            }
            if (live) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(5.dp)
                        .background(scheme.primary, CircleShape),
                )
            }
        }
    }
}

/** 未排课清单：只列课程名、学分与开课单位，不进时间网格。 */
@Composable
private fun NoScheduleSection(courses: List<NoScheduleCourse>) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = "未排课",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 18.dp, bottom = 6.dp),
        )
        QduCard(padded = false, modifier = Modifier.padding(horizontal = 16.dp)) {
            courses.forEachIndexed { index, course ->
                ListRow(
                    title = course.name,
                    subtitle = listOf(
                        if (course.credits > 0) "${Formats.credit(course.credits.toDouble())} 学分" else "",
                        course.department,
                    ).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifBlank { "学分与开课单位未标注" },
                    chevron = false,
                    divider = index != courses.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    course: Course,
    onCopyRoom: () -> Unit,
    onDismiss: () -> Unit,
) {
    val meta = joinInfo(course.department, course.category, course.campus)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(course.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DetailRow("教师", course.teachers.ifBlank { "未标注" })
                DetailRow("周次", course.weekLabel.ifBlank { "未标注" })
                DetailRow("教室", course.room.ifBlank { "未标注" })
                DetailRow(
                    "节次",
                    "${Formats.weekdayLabel(course.day)} ${course.sectionLabel}" +
                        if (course.startTime.isNotBlank()) " ${course.startTime}-${course.endTime}" else "",
                )
                DetailRow(
                    "教学班",
                    joinInfo(
                        course.classNo,
                        if (course.code.isNotBlank()) "课程编号 ${course.code}" else "",
                    ).ifBlank { "—" },
                )
                DetailRow("学分 / 学时", "${Formats.credit(course.credits)} / ${course.hours}")
            }
        },
        confirmButton = {
            DialogButton(
                text = "复制教室",
                kind = QduButtonKind.Primary,
                enabled = course.room.isNotBlank(),
                onClick = onCopyRoom,
            )
        },
        dismissButton = {
            DialogButton(
                text = "关闭",
                kind = QduButtonKind.Ghost,
                onClick = onDismiss,
            )
        },
    )
}

/** 本周说明卡：把因周次规则而看不到的课点名，原型 04 屏的同名区块。 */
@Composable
private fun WeekNoteCard(notes: List<String>) {
    val scheme = MaterialTheme.colorScheme
    val shown = notes.take(MaxWeekNotes)
    QduCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            text = "本周说明",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        shown.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (notes.size > shown.size) {
            Text(
                text = "另有 ${notes.size - shown.size} 门课程本周不上",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

/** 页脚：更新时刻与「当前节次」图例。图例只在标记真的画出来时才给。 */
@Composable
private fun FootLine(updatedAt: Long?, hasLiveCourse: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (updatedAt != null) "更新于 ${Formats.dateTime(updatedAt)}" else "尚未成功同步",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (hasLiveCourse) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(scheme.primary, CircleShape),
                )
                Text(
                    text = "当前节次",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 若干段信息拼成一行，空的自动去掉，缺字段时不留孤零零的分隔符。 */
private fun joinInfo(vararg parts: String): String =
    parts.filter { it.isNotBlank() }.joinToString(" · ")

/**
 * 第一次进课表时问一次课表从哪来。
 *
 * 学校系统认不出「这个人是本科生还是研究生」，只能问；选完之后这个卡片不再出现，
 * 想改在「学期与节次」里随时能改。
 */
@Composable
private fun LevelChoiceBlock(
    onGraduate: () -> Unit,
    onUndergraduate: () -> Unit,
) {
    QduCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(text = "你的课表来自哪里", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "本科在读用教务处公开的课程总表，勾选自己的班级就有课表，不用登录；" +
                    "研究生在读走研究生系统，需要单独登一次。选错了以后还能改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QduButton(
                text = "本科在读",
                onClick = onUndergraduate,
                kind = QduButtonKind.Primary,
                modifier = Modifier.fillMaxWidth(),
            )
            QduButton(
                text = "研究生在读",
                onClick = onGraduate,
                kind = QduButtonKind.Tonal,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private const val MaxWeekNotes = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(
    semesterId: String,
    termStartDate: String,
    sections: List<ClassSection>,
    overrides: Map<Int, Pair<String, String>>,
    level: TimetableLevel?,
    semesterOptions: List<SemesterOption>,
    classCount: Int,
    onDismiss: () -> Unit,
    onPickClasses: () -> Unit,
    onSave: (TimetableLevel, String, String, Map<Int, Pair<String, String>>) -> Unit,
) {
    // 来源也是草稿：点胶囊只改弹窗内的选择，点「保存」才切换数据源，取消时原样保留
    var draftLevel by remember(level) { mutableStateOf(level ?: TimetableLevel.Graduate) }
    val undergraduate = draftLevel == TimetableLevel.Undergraduate
    var semester by remember(semesterId) { mutableStateOf(semesterId) }
    var startDate by remember(termStartDate) { mutableStateOf(termStartDate) }
    var draft by remember(overrides) { mutableStateOf(overrides) }
    var semesterMenu by remember { mutableStateOf(false) }
    var showStartPicker by remember { mutableStateOf(false) }
    // 本科的学期候选来自随包的课程总表，研究生那一路仍按标识规则推候选——两边给不出同一份列表。
    val choices = remember(semester, level, semesterOptions) {
        if (undergraduate) {
            semesterOptions.ifEmpty { listOf(SemesterOption(id = semester, label = semester)) }
        } else {
            semesterChoices(semester).map { SemesterOption(id = it, label = it) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学期与节次") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "课表来源",
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = undergraduate,
                        onClick = { draftLevel = TimetableLevel.Undergraduate },
                        label = { Text("本科生课程总表") },
                    )
                    FilterChip(
                        selected = !undergraduate,
                        onClick = { draftLevel = TimetableLevel.Graduate },
                        label = { Text("研究生系统") },
                    )
                }
                if (undergraduate) {
                    Text(
                        text = if (classCount == 0) "还没有勾选班级，课表是空的。"
                        else "已勾 $classCount 个班，改班级下面的按钮随时能调。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    QduButton(
                        text = "选择我的班级",
                        onClick = onPickClasses,
                        kind = QduButtonKind.Ghost,
                        small = true,
                    )
                }
                Box {
                    PickerField(
                        label = "学期",
                        value = choices.firstOrNull { it.id == semester }?.label ?: semester,
                        onClick = { semesterMenu = true },
                    )
                    DropdownMenu(
                        expanded = semesterMenu,
                        onDismissRequest = { semesterMenu = false },
                    ) {
                        choices.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    semester = option.id
                                    // 课程总表把第一周周一一起带着，选学期就不用再照校历填一次
                                    if (option.termStart.isNotBlank()) startDate = option.termStart
                                    semesterMenu = false
                                },
                            )
                        }
                    }
                }
                PickerField(
                    label = "学期起始日",
                    value = startDate.ifBlank { "未设置" },
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = { showStartPicker = true },
                )
                if (sections.isEmpty()) {
                    Text(
                        text = "同步课表后这里会列出节次，可逐节覆盖起止时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "节次时间",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "默认取自学期配置；与默认一致时不保存覆盖。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    sections.forEach { section ->
                        val times = draft[section.index]
                            ?: (section.startTime to section.endTime)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "${section.index}. " +
                                    section.label.ifBlank { section.period },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeField(
                                    label = "开始",
                                    value = times.first,
                                    modifier = Modifier.weight(1f),
                                    onValue = {
                                        draft = draft + (section.index to (it to times.second))
                                    },
                                )
                                TimeField(
                                    label = "结束",
                                    value = times.second,
                                    modifier = Modifier.weight(1f),
                                    onValue = {
                                        draft = draft + (section.index to (times.first to it))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            DialogButton(
                text = "保存",
                kind = QduButtonKind.Primary,
                onClick = {
                    val cleaned = sections.mapNotNull { section ->
                        val (start, end) = draft[section.index] ?: return@mapNotNull null
                        if (start.isBlank() || end.isBlank()) return@mapNotNull null
                        if (start == section.startTime && end == section.endTime) {
                            return@mapNotNull null
                        }
                        section.index to (start to end)
                    }.toMap()
                    onSave(draftLevel, semester, startDate, cleaned)
                },
            )
        },
        dismissButton = {
            DialogButton(
                text = "取消",
                kind = QduButtonKind.Ghost,
                onClick = onDismiss,
            )
        },
    )

    if (showStartPicker) {
        val initial = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: LocalDate.now()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                DialogButton(
                    text = "确定",
                    kind = QduButtonKind.Primary,
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            startDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString()
                        }
                        showStartPicker = false
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { showStartPicker = false },
                )
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** 选择框：描边卡片加标签与当前值，点击打开对应的选择器。 */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.ExpandMore,
) {
    OutlinedCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var show by remember { mutableStateOf(false) }
    PickerField(
        label = label,
        value = value.ifBlank { "未设置" },
        icon = Icons.Outlined.Schedule,
        onClick = { show = true },
        modifier = modifier,
    )
    if (show) {
        val initial = parseTime(value)
        val state = rememberTimePickerState(
            initialHour = initial?.hour ?: 8,
            initialMinute = initial?.minute ?: 0,
            is24Hour = true,
        )
        TimePickerDialog(
            onDismissRequest = { show = false },
            title = { Text("选择${label}时间") },
            confirmButton = {
                DialogButton(
                    text = "确定",
                    kind = QduButtonKind.Primary,
                    onClick = {
                        onValue("%02d:%02d".format(state.hour, state.minute))
                        show = false
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { show = false },
                )
            },
        ) {
            TimePicker(state = state)
        }
    }
}

private fun parseTime(value: String): LocalTime? =
    value.trim().takeIf { it.isNotEmpty() }
        ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

/**
 * 学期选择框的候选：当前学期往前 6 个。
 * 学校按 9 月与次年 2 月分学期，这里只按月份推算候选，不改变已存的值。
 */
private fun semesterChoices(current: String, today: LocalDate = LocalDate.now()): List<String> {
    var academicStart = if (today.monthValue in 2..8) today.year - 1 else today.year
    var term = if (today.monthValue in 2..8) 2 else 1
    val options = buildList {
        repeat(6) {
            add("$academicStart-${academicStart + 1}-$term")
            if (term == 1) {
                term = 2
                academicStart -= 1
            } else {
                term = 1
            }
        }
    }
    return if (current.isBlank() || current in options) options else listOf(current) + options
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
