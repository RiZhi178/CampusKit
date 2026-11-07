package qdu.campus.feature.notice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qdu.campus.data.remote.notice.Block
import qdu.campus.data.remote.notice.NoticeDetail
import qdu.campus.data.remote.notice.NoticeDetailParser
import qdu.campus.domain.repository.NoticeRepository
import qdu.campus.platform.designsystem.AppLoading
import qdu.campus.platform.designsystem.PlaceholderBlock
import qdu.campus.platform.designsystem.QduCard
import qdu.campus.platform.designsystem.QduButton
import qdu.campus.platform.designsystem.QduButtonKind
import qdu.campus.platform.designsystem.SectionCard
import qdu.campus.platform.system.ExternalLauncher
import java.net.URLDecoder
import javax.inject.Inject

/**
 * 通知详情原生化（2026-11-07 切片）：正文抓取 + 解析 + 原生渲染；
 * 附件沿用 PRD F4-2 的口径交给系统浏览器，内嵌 PDF 同样外开；
 * 顶栏「原网页」保留 WebView 兜底，覆盖非博达 CMS 的特殊页面。
 */
@HiltViewModel
class NoticeDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NoticeRepository,
    private val launcher: ExternalLauncher,
) : ViewModel() {

    /** 路由参数在 NavHost 侧已 URL 编码，这里解码；URL 失真就抓不到，页面会给出错误态。 */
    val url: String = URLDecoder.decode(savedStateHandle.get<String>("url").orEmpty(), "UTF-8")
    val title: String = URLDecoder.decode(savedStateHandle.get<String>("title").orEmpty(), "UTF-8")
        .ifBlank { "通知详情" }

    sealed interface UiState {
        data object Loading : UiState
        data class Ready(val detail: NoticeDetail) : UiState
        data class Error(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        fetch()
    }

    fun fetch() {
        if (url.isBlank()) {
            _state.value = UiState.Error("通知链接缺失")
            return
        }
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { repository.fetchDetail(url) }
                .fold(
                    onSuccess = { detail ->
                        detail?.let { UiState.Ready(it) }
                            ?: UiState.Error("详情抓取失败，可能是网络不通或页面结构变化")
                    },
                    onFailure = { UiState.Error("详情抓取失败：${it.message ?: "网络异常"}") },
                )
        }
    }

    /** 附件与内嵌 PDF 交给系统浏览器（PRD F4-2 口径），App 内不做下载。 */
    fun openExternally(link: String) {
        launcher.openUrl(link)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeDetailScreen(
    onBack: () -> Unit,
    onOpenWeb: (url: String, title: String) -> Unit,
    viewModel: NoticeDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(viewModel.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // WebView 兜底：特殊页面（非博达 CMS 正文）从这里进原网页
                    IconButton(
                        onClick = { onOpenWeb(viewModel.url, viewModel.title) },
                        modifier = Modifier.semantics { contentDescription = "打开原网页" },
                    ) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            NoticeDetailViewModel.UiState.Loading -> AppLoading(Modifier.padding(padding))
            is NoticeDetailViewModel.UiState.Error -> ErrorState(
                message = s.message,
                onRetry = viewModel::fetch,
                onOpenWeb = { onOpenWeb(viewModel.url, viewModel.title) },
                modifier = Modifier.padding(padding),
            )
            is NoticeDetailViewModel.UiState.Ready -> DetailBody(
                detail = s.detail,
                onOpenExternally = viewModel::openExternally,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun DetailBody(
    detail: NoticeDetail,
    onOpenExternally: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = detail.title.ifBlank { "通知详情" },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (detail.publishedDate.isNotBlank()) {
                    Text(
                        text = detail.publishedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
        }
        itemsIndexed(detail.blocks) { _, block ->
            when (block) {
                is Block.Paragraph -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )

                is Block.Image -> AsyncImage(
                    model = block.url,
                    contentDescription = "通知配图",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(MaterialTheme.shapes.small)
                        .aspectRatio(4f / 3f)
                        .clickable { onOpenExternally(block.url) },
                )

                is Block.EmbeddedPdf -> QduCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onOpenExternally(block.url) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "正文为内嵌 PDF，点击查看原文",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        if (detail.attachments.isNotEmpty()) {
            item {
                SectionCard(title = "附件") {
                    detail.attachments.forEachIndexed { index, attachment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenExternally(attachment.url) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = attachment.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = "打开附件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (index != detail.attachments.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
            item {
                Text(
                    text = "附件由系统浏览器下载（须从通知页跳转，直接下载会被拦截）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        item { Spacer(Modifier.padding(bottom = 24.dp)) }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    onOpenWeb: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PlaceholderBlock(
            title = "详情没能打开",
            description = message,
            modifier = Modifier.fillMaxWidth(),
            actionLabel = "重试",
            onAction = onRetry,
        )
        Spacer(Modifier.padding(vertical = 10.dp))
        QduButton(text = "打开原网页", onClick = onOpenWeb, kind = QduButtonKind.Tonal)
    }
}
