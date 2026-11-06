package qdu.campus.feature.electricity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.system.ExternalLauncher

/**
 * 房间编号的绑定页。楼栋候选来自随包的报修系统楼栋字典（166 栋 / 8 校区，
 * 见 `docs/interfaces/baoxiu.md` 的「校区与楼栋字典」节）：先选校区、再选楼栋，
 * 简称能推出来的直接进编码；推不出来的给手填框。列表外楼栋留手填入口。
 *
 * 单元沿用人工整理的结构（汇园 A/B/C、泓园 北/南/东、滢园 2–8 号楼东/西、浮山公寓 1/2/3），
 * 按简称对上就给下拉，对不上按无单元处理。
 *
 * 版面顺序按使用顺序排：先选校区与楼栋、再填房号（同一行），结果落在下方，
 * 底部只留一个动作——核对编号并绑定，绑定后进宿舍电费页看余额与剩余电量。
 * 核对不通过（服务端回「没有账户」）时不绑定，但保留「仍然去充值」这条旧出口，
 * 免得编号规则变动把人彻底挡在充值页外面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCodeScreen(
    onBack: () -> Unit,
    onBound: () -> Unit,
    onOpenRecharge: () -> Unit,
    viewModel: RoomCodeViewModel = hiltViewModel(),
) {
    val bindState by viewModel.state.collectAsStateWithLifecycle()
    val catalog = bindState.catalog
    var campus by remember { mutableStateOf<CatalogCampus?>(null) }
    var campusMenu by remember { mutableStateOf(false) }
    var building by remember { mutableStateOf<DormBuilding?>(null) }
    var manual by remember { mutableStateOf(false) }
    var manualBuilding by remember { mutableStateOf("") }
    var manualUnit by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("") }
    var room by remember { mutableStateOf("") }
    var buildingMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current

    val campusBuildings = campus?.buildings.orEmpty()
    val buildingCode = if (manual) manualBuilding.trim() else building?.code.orEmpty()
    // 房号 3 位：首位楼层 1–8，后两位房间号 01–64
    val roomValid = room.length == 3 &&
        room[0] in '1'..'8' &&
        room.substring(1).toInt() in 1..64
    val roomHint = when {
        room.isEmpty() -> null
        room.length != 3 -> "房号为 3 位，首位楼层，后两位房间号"
        room[0] !in '1'..'8' -> "楼层只能是 1–8"
        room.substring(1).toInt() !in 1..64 -> "房间号只能是 01–64"
        else -> null
    }
    val unitCode = when {
        manual -> manualUnit.trim().ifBlank { "1" }
        building == null -> "1"
        building!!.units.isEmpty() -> "1"
        else -> unit.ifBlank { building!!.defaultUnit }
    }
    val code = if (roomValid) RoomCodeBuilder.build(buildingCode, unitCode, room) else ""
    val ready = buildingCode.isNotBlank() && roomValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("绑定宿舍房号") },
                navigationIcon = { QduBackButton(onClick = onBack) },
            )
        },
        contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 校区一行：候选来自随包字典，八个校区
            ExposedDropdownMenuBox(
                expanded = campusMenu,
                onExpandedChange = { campusMenu = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = campus?.campus.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("校区") },
                    placeholder = { Text(if (catalog.isEmpty()) "楼栋字典不可用，请手填简称" else "选择校区") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(campusMenu) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(
                    expanded = campusMenu,
                    onDismissRequest = { campusMenu = false },
                ) {
                    catalog.forEach { item ->
                        DropdownMenuItem(
                            text = { Text("${item.campus}（${item.buildings.size} 栋）") },
                            onClick = {
                                campus = item
                                building = null
                                manual = false
                                viewModel.clear()
                                campusMenu = false
                            },
                        )
                    }
                }
            }

            // 选楼栋 + 填房号，同一行；楼栋列表长，占宽一些
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ExposedDropdownMenuBox(
                    expanded = buildingMenu,
                    onExpandedChange = { buildingMenu = it },
                    modifier = Modifier.weight(1.15f),
                ) {
                    OutlinedTextField(
                        value = when {
                            manual -> "其他楼栋（手填）"
                            building != null -> building!!.label
                            else -> ""
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("楼栋") },
                        placeholder = { Text(if (campus == null) "先选校区" else "选择宿舍楼") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(buildingMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = buildingMenu,
                        onDismissRequest = { buildingMenu = false },
                    ) {
                        campusBuildings.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (item.code != null) "${item.name}（${item.code}）"
                                        else "${item.name}（简称手填）",
                                    )
                                },
                                onClick = {
                                    // 简称能推出来的直接进编码；推不出来的切到手填，简称让用户补
                                    val units = QduDormBuildings
                                        .firstOrNull { it.code == item.code }
                                        ?.units
                                        .orEmpty()
                                    building = DormBuilding(
                                        park = campus?.campus.orEmpty(),
                                        label = item.name,
                                        code = item.code.orEmpty(),
                                        units = units,
                                    )
                                    manual = item.code == null
                                    if (manual) manualBuilding = ""
                                    unit = ""
                                    buildingMenu = false
                                    viewModel.clear()
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("其他楼栋（手填简称）") },
                            onClick = {
                                manual = true
                                building = null
                                buildingMenu = false
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = room,
                    onValueChange = {
                        room = it.filter(Char::isDigit).take(3)
                        viewModel.clear()
                    },
                    label = { Text("房号") },
                    placeholder = { Text("101") },
                    singleLine = true,
                    isError = roomHint != null,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                    modifier = Modifier.weight(0.85f),
                )
            }

            // 单元 / 手填简称，只在需要时出现
            if (manual) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = manualBuilding,
                        onValueChange = { manualBuilding = it },
                        label = { Text("楼栋简称，如 湉1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1.15f),
                    )
                    OutlinedTextField(
                        value = manualUnit,
                        onValueChange = { manualUnit = it },
                        label = { Text("单元") },
                        placeholder = { Text("无填 1") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.weight(0.85f),
                    )
                }
            } else if (building != null && building!!.code.isBlank()) {
                Text(
                    text = "这栋楼的简称没有公开依据，请在上方「楼栋简称」里填财务处的写法（如 湉1）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (building != null && building!!.units.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = unitMenu,
                    onExpandedChange = { unitMenu = it },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = unit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("单元") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitMenu) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = unitMenu,
                        onDismissRequest = { unitMenu = false },
                    ) {
                        building!!.units.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    unit = option
                                    unitMenu = false
                                },
                            )
                        }
                    }
                }
            } else if (building != null) {
                Text(
                    text = "该楼无单元，编码中的单元位固定为 1。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            roomHint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // 结果在下
            Text(
                text = "生成结果",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = code.ifBlank { "—" },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "示例：浩1-1-101、浏A-1-101、汇1-A-101、泓5-北-101",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // 服务端原话是「第三方接口响应：对应房间没有账户，接口名称…，URL…」，
                // 后面两段是给运维看的，这里只留用户改得动的那半句。
                text = "绑定前会先向电费系统核对这个编号；查不到会提示「对应房间没有账户」。这一步只是查询，不产生待支付单。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // 校验结果：编号存在时退回宿舍电费页显示，这里只处理被服务端拒绝的那一种
            bindState.rejected?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                QduButton(
                    text = "仍然去充值",
                    kind = QduButtonKind.Ghost,
                    small = true,
                    enabled = ready,
                    onClick = {
                        ExternalLauncher(context).copyToClipboard(code, "充值编号")
                        onOpenRecharge()
                    },
                )
            }

            Spacer(Modifier.weight(1f))

            // 核对通过才落盘；核对不通过（服务端回「没有账户」）不绑定，只留上面那条旧出口
            Button(
                onClick = { viewModel.bind(code, onBound) },
                enabled = ready && !bindState.checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (bindState.checking) "正在核对房间…" else "核对并绑定")
            }
        }
    }
}
