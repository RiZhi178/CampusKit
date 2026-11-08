package qdu.campus.feature.baoxiu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.SessionGate
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.SectionCard

/**
 * 「我要报修」原生表单。
 *
 * 几处按移动端表单惯例处理：
 * - 依赖关系明说：没选校区之前楼栋不可点，副标题直接写「请先选择校区」，不做点了没反应。
 * - 必填项在标签上带星号，提交前本地校验，错误集中在按钮上方一条，不逐字段弹窗。
 * - 联系方式用数字键盘，减少输错；站点要求 11 位手机号，本地先拦。
 * - 底部留 [imePadding]，键盘弹起时提交按钮不会被挡住。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaoxiuFormScreen(
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: BaoxiuFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheet by remember { mutableStateOf<SheetKind?>(null) }
    var photoLabel by remember { mutableStateOf("") }

    LaunchedEffect(state.done) {
        if (state.done) onSubmitted()
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            } ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                else -> "jpg"
            }
            photoLabel = formatSize(bytes.size)
            viewModel.attachPhoto(uri.toString(), bytes, "photo.$ext", mime)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我要报修") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loginRequired -> SessionGate(
                    title = "宿舍报修需要先登录",
                    reason = "登录后就可以提交报修了。",
                    onLogin = onOpenWebLogin,
                    modifier = Modifier.fillMaxSize(),
                )

                state.loading -> AppLoading(modifier = Modifier.fillMaxSize())

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SectionCard(title = "报修人") {
                        Text(
                            text = "姓名与学号取自统一认证会话，不需要重复填写",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = state.draft.phone,
                            onValueChange = viewModel::setPhone,
                            label = { Text("联系方式 *") },
                            placeholder = { Text("11 位手机号") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Phone,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }

                    SectionCard(title = "故障地点") {
                        OptionRow(
                            label = "校区 *",
                            value = state.draft.campusName,
                            onClick = { sheet = SheetKind.Campus },
                        )
                        val campusReady = state.draft.campusId.isNotBlank()
                        OptionRow(
                            label = "楼栋 *",
                            value = state.draft.buildingName,
                            placeholder = if (campusReady) "请选择楼栋" else "请先选择校区",
                            enabled = campusReady,
                            onClick = { if (campusReady) sheet = SheetKind.Building },
                        )
                        OutlinedTextField(
                            value = state.draft.detailAddress,
                            onValueChange = viewModel::setAddress,
                            label = { Text("详细地址 *") },
                            placeholder = { Text("楼号与房间号，例如 103") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }

                    SectionCard(title = "故障内容") {
                        OptionRow(
                            label = "事项分组 *",
                            value = state.draft.groupName,
                            onClick = { sheet = SheetKind.Group },
                        )
                        val groupReady = state.draft.groupId.isNotBlank()
                        OptionRow(
                            label = "事项 *",
                            value = state.draft.itemName,
                            placeholder = if (groupReady) "请选择事项" else "请先选择分组",
                            enabled = groupReady,
                            onClick = { if (groupReady) sheet = SheetKind.Item },
                        )
                        OutlinedTextField(
                            value = state.draft.desc,
                            onValueChange = viewModel::setDesc,
                            label = { Text("报修描述 *") },
                            placeholder = { Text("例如：门锁坏了，无法锁门") },
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                        PhotoPicker(
                            label = photoLabel,
                            selected = state.photoUri != null,
                            onPick = { pickImage.launch("image/*") },
                            onClear = {
                                photoLabel = ""
                                viewModel.clearPhoto()
                            },
                        )
                    }

                    if (state.error != null) {
                        Text(
                            text = state.error.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    QduButton(
                        text = if (state.submitting) "正在提交…" else "提交报修",
                        onClick = viewModel::submit,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.submitting) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = "正在上传图片并提交，请不要退出",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = "提交后由后勤派单；要撤回需在工单详情里填写撤销原因。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    val options = when (sheet) {
        SheetKind.Campus -> state.campuses
        SheetKind.Building -> state.buildings
        SheetKind.Group -> state.groups
        SheetKind.Item -> state.items
        null -> emptyList()
    }
    if (sheet != null) {
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                if (options.isEmpty()) {
                    Text(
                        text = "没有可选项",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                options.forEach { option ->
                    ListRow(
                        title = option.label,
                        chevron = false,
                        onClick = {
                            when (sheet) {
                                SheetKind.Campus -> viewModel.selectCampus(option)
                                SheetKind.Building -> viewModel.selectBuilding(option)
                                SheetKind.Group -> viewModel.selectGroup(option)
                                SheetKind.Item -> viewModel.selectItem(option)
                                null -> Unit
                            }
                            sheet = null
                        },
                    )
                }
            }
        }
    }
}

private enum class SheetKind { Campus, Building, Group, Item }

@Composable
private fun OptionRow(
    label: String,
    value: String,
    placeholder: String = "请选择",
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    ListRow(
        title = label,
        subtitle = value.ifBlank { placeholder },
        chevron = enabled,
        onClick = if (enabled) onClick else null,
        titleColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * 图片位。选中后不再放大缩略图（本机没有图片加载库），改为把文件名与体积写清楚，
 * 让人确认「选中的是不是那张」。
 */
@Composable
private fun PhotoPicker(
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clickable(onClick = onPick)
                .semantics {
                    contentDescription = if (selected) "已选报修图片，点击重新选择" else "选择报修图片"
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selected) {
                IconButton(onClick = onClear, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "移除已选图片",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (selected) "已选择 $label" else "报修图片 *",
                style = MaterialTheme.typography.bodyMedium,
            )
            QduButton(
                text = if (selected) "重新选择" else "选择图片",
                onClick = onPick,
                kind = if (selected) QduButtonKind.Ghost else QduButtonKind.Tonal,
                small = true,
            )
        }
    }
}

/** 体积只用来让人确认选对了文件，精确到 0.1 MB 就够。 */
private fun formatSize(bytes: Int): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "${kb.toInt()} KB"
    return String.format(java.util.Locale.CHINA, "%.1f MB", kb / 1024)
}
