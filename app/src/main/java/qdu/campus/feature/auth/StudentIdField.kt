package qdu.campus.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import qdu.campus.platform.designsystem.QduField

/**
 * 学号输入。登录不再单设页面，引导页是唯一入口。
 * 只收集学号：密码始终由用户在校方页面内输入，本应用不读也不存。
 */
@Composable
fun StudentIdField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    Column(modifier = modifier) {
        Text(
            text = "学号",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QduField(
            value = value,
            onValueChange = { if (it.length <= 20) onValueChange(it) },
            modifier = Modifier,
            placeholder = "例如 20242012340137",
            supportingText = "选填。只用于在统一认证页替你填好学号，密码仍由你自己输入",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
        )
    }
}
