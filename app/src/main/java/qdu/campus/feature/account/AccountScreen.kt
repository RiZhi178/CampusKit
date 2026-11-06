package qdu.campus.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import qdu.campus.domain.model.SessionState
import qdu.campus.platform.designsystem.AccountCard
import qdu.campus.platform.designsystem.GroupHead
import qdu.campus.platform.designsystem.ListRow
import qdu.campus.platform.designsystem.QduBackButton
import qdu.campus.platform.designsystem.DialogButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.UiSpacing
import qdu.campus.platform.designsystem.listBottomGuard

/**
 * 账号二级页。更多页只放一张账号卡，退出登录与切换账号收在这里，
 * 与卡面信息同处一屏：看清是谁、再决定退不退。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSwitchAccount: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }
    var confirmSwitch by remember { mutableStateOf(false) }
    val signedIn = state.session == SessionState.Valid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("账号") },
                navigationIcon = { QduBackButton(onClick = onBack) },
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.ime),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = UiSpacing.PageHorizontal),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            AccountCard(
                state = accountCardState(state.session, state.studentId, state.userName),
                compact = false,
                onSignIn = onOpenLogin,
                modifier = Modifier.padding(top = 8.dp),
            )

            GroupHead(text = "本机会话")
            QduCard(padded = false) {
                if (signedIn) {
                    ListRow(
                        title = "退出登录",
                        subtitle = "清除本机会话凭据，学号留着下次预填",
                        subtitleMaxLines = 1,
                        leading = Icons.AutoMirrored.Outlined.Logout,
                        leadingTint = MaterialTheme.colorScheme.error,
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { confirmLogout = true },
                    )
                }
                ListRow(
                    title = "切换账号",
                    subtitle = "连学号一起清除，回到引导页重填",
                    subtitleMaxLines = 1,
                    leading = Icons.Outlined.Person,
                    divider = false,
                    onClick = { confirmSwitch = true },
                )
            }

            Text(
                text = "退出只会清掉本机保存的票，不会改变账号在服务端的状态。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = UiSpacing.SectionTop, bottom = listBottomGuard()),
            )
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("会清除本机会话凭据，图书馆、学工、座位与办事大厅需要重新登录；学号留着。") },
            confirmButton = {
                DialogButton(
                    text = "退出",
                    kind = QduButtonKind.Danger,
                    onClick = {
                        viewModel.logout()
                        confirmLogout = false
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { confirmLogout = false },
                )
            },
        )
    }

    if (confirmSwitch) {
        AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text("切换账号？") },
            text = { Text("会清除本机凭据与学号，回到引导页重新填写。") },
            confirmButton = {
                DialogButton(
                    text = "切换",
                    kind = QduButtonKind.Danger,
                    onClick = {
                        confirmSwitch = false
                        viewModel.switchAccount(onSwitchAccount)
                    },
                )
            },
            dismissButton = {
                DialogButton(
                    text = "取消",
                    kind = QduButtonKind.Ghost,
                    onClick = { confirmSwitch = false },
                )
            },
        )
    }
}
