package qdu.campus.feature.baoxiu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import qdu.campus.domain.model.RepairDetail
import qdu.campus.domain.model.RepairFlowNode
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.Pill
import qdu.campus.platform.designsystem.PillKind
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.SectionCard
import qdu.campus.platform.designsystem.StatusLevel

/**
 * 工单详情。字段只读，进度用时间序排列；`state=20`（等待调度）才给「撤销」，
 * 且撤销原因必填——网页端同样如此，空原因只弹提示、不发请求。
 *
 * 撤销是不可撤销不可撤回的终态操作，所以按钮走危险色 + 二次确认，
 * 确认按钮在请求返回前保持禁用，避免连点重复提交。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaoxiuDetailScreen(
    onBack: () -> Unit,
    onCancelled: () -> Unit,
    onOpenWebLogin: () -> Unit,
    viewModel: BaoxiuDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showCancel by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var reasonError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.cancelled) {
        if (state.cancelled) onCancelled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("工单详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loginRequired -> Centered(
                    title = "需要重新登录报修系统",
                    description = "统一认证会话过期后，报修接口会拒绝请求。",
                    actionLabel = "去登录",
                    onAction = onOpenWebLogin,
                )

                state.loading -> AppLoading(modifier = Modifier.fillMaxSize())

                state.error != null -> Centered(
                    title = "详情没能加载出来",
                    description = state.error.orEmpty(),
                    actionLabel = "重试",
                    onAction = { viewModel.load() },
                )

                state.detail != null -> DetailContent(
                    detail = state.detail!!,
                    cancelling = state.cancelling,
                    actionError = state.actionError,
                    onCancel = { reason = ""; reasonError = null; showCancel = true },
                )
            }
        }
    }

    if (showCancel) {
        AlertDialog(
            onDismissRequest = { showCancel = false },
            title = { Text("撤销报修") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "撤销后工单进入终态，不能再恢复。原因会记入进度流水。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = {
                            reason = it
                            if (reasonError != null) reasonError = null
                        },
                        label = { Text("原因") },
                        placeholder = { Text("例如：报错楼栋，重新提交") },
                        singleLine = true,
                        isError = reasonError != null,
                        supportingText = reasonError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                DialogButton(
                    text = "确认撤销",
                    kind = QduButtonKind.Primary,
                    enabled = !state.cancelling,
                    onClick = {
                        if (reason.isBlank()) {
                            // 站点同样要求必填；留在弹窗里提示，不让用户白跑一趟。
                            reasonError = "请填写撤销原因"
                        } else {
                            showCancel = false
                            viewModel.cancel(reason)
                        }
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { showCancel = false },
                )
            },
        )
    }
}

@Composable
private fun DetailContent(
    detail: RepairDetail,
    cancelling: Boolean,
    actionError: String?,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard(
            title = "报修信息",
            // 状态放在标题行尾，和标题同一视线高度，不用往下找。
            trailing = if (detail.stateText.isBlank()) null else {
                { Pill(text = detail.stateText, kind = stateKind(detail.state), mark = stateMark(detail.state)) }
            },
        ) {
            InfoRow("报修编号", detail.id)
            InfoRow("报修时间", detail.applyTime)
            InfoRow("报修项目", detail.itemPath)
            InfoRow("报修地点", detail.location)
            InfoRow("报修内容", detail.desc)
            if (detail.phone.isNotBlank()) InfoRow("联系方式", detail.phone)
        }

        SectionCard(title = "报修进度") {
            if (detail.flow.isEmpty()) {
                Text(
                    text = "暂时没有进度记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                detail.flow.forEachIndexed { index, node ->
                    FlowItem(node = node, isLast = index == detail.flow.lastIndex)
                }
            }
        }

        if (detail.state == "20") {
            QduButton(
                text = if (cancelling) "正在撤销…" else "撤销报修",
                onClick = onCancel,
                kind = QduButtonKind.Danger,
                enabled = !cancelling,
                modifier = Modifier.fillMaxWidth(),
            )
            if (actionError != null) {
                Text(
                    text = actionError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun stateKind(state: String?): PillKind = when (state) {
    "20" -> PillKind.Warn
    "12" -> PillKind.Default
    else -> PillKind.Default
}

private fun stateMark(state: String?): StatusLevel? = when (state) {
    "20" -> StatusLevel.WARNING
    "12" -> StatusLevel.BLOCKED
    else -> null
}

@Composable
private fun InfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FlowItem(node: RepairFlowNode, isLast: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "·",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!isLast) {
                Text(
                    text = "|",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
        Column(
            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = node.title, style = MaterialTheme.typography.bodyMedium)
            if (node.remark.isNotBlank()) {
                Text(
                    text = node.remark,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (node.time.isNotBlank()) {
                Text(
                    text = node.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Centered(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QduCard {
            PlaceholderBlock(
                title = title,
                description = description,
                actionLabel = actionLabel,
                onAction = onAction,
            )
        }
    }
}
