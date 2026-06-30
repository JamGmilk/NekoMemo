package mirujam.nekomemo.ui.fetcher

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.navigation.Route
import mirujam.nekomemo.ui.component.AppTopBar
import mirujam.nekomemo.ui.component.LocalSnackbarHostState
import mirujam.nekomemo.ui.theme.AppShapes
import mirujam.nekomemo.ui.theme.ProgressIndicatorThinShapes
import timber.log.Timber

private class WebViewRef {
    var webView: WebView? = null
}

@SuppressLint("SetJavaScriptEnabled", "LocalContextGetResourceValueCall")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FetcherScreen(
    onNavigateToExtract: () -> Unit,
    onBack: () -> Unit,
    viewModel: FetcherViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isParsing = uiState.isParsing
    val parseResult = uiState.parseResult
    val currentUrl = uiState.currentUrl.ifBlank { "https://i.chaoxing.com" }
    val navigateToExtract = uiState.navigateToExtract

    var showHtmlSheet by rememberSaveable { mutableStateOf(false) }
    var htmlContent by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scrollState = rememberScrollState()

    var isLoading by rememberSaveable { mutableStateOf(false) }
    var loadProgress by rememberSaveable { mutableIntStateOf(0) }
    var isZoomControlsVisible by rememberSaveable { mutableStateOf(false) }
    var zoomPercent by rememberSaveable { mutableIntStateOf(100) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current
    val localContext = LocalContext.current
    val isSnackbarVisible = snackbarHostState.currentSnackbarData != null
    val fabPadding by animateDpAsState(targetValue = if (isSnackbarVisible) 64.dp else 0.dp, label = "fabPadding")

    val webViewRef = remember { WebViewRef() }
    var webViewState by rememberSaveable { mutableStateOf<Bundle?>(null) }
    var pageTitle by rememberSaveable { mutableStateOf("") }
    var webViewHeight by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    fun WebView.applyPageZoom(percent: Int) {
        val scale = percent.coerceIn(50, 200) / 100.0
        evaluateJavascript(
            """
            (function() {
                var scale = $scale;
                var html = document.documentElement;
                var body = document.body;
                if (html) {
                    html.style.zoom = scale;
                    html.style.transform = 'none';
                    html.style.transformOrigin = 'top left';
                }
                if (body) {
                    body.style.zoom = scale;
                    body.style.transform = 'none';
                    body.style.transformOrigin = 'top left';
                }
                return true;
            })();
            """.trimIndent(),
            null
        )
    }

    fun applyZoom(percent: Int) {
        zoomPercent = percent.coerceIn(50, 200)
        webViewRef.webView?.applyPageZoom(zoomPercent)
    }

    fun WebView.fixBodyHeight() {
        evaluateJavascript(
            """
            (function() {
                function fixHeight() {
                    var body = document.body;
                    var html = document.documentElement;
                    if (!body) return 0;
                    var bodyHeight = Math.max(
                        body.scrollHeight,
                        body.offsetHeight,
                        body.clientHeight
                    );
                    var htmlHeight = Math.max(
                        html.scrollHeight,
                        html.offsetHeight,
                        html.clientHeight
                    );
                    var contentHeight = Math.max(bodyHeight, htmlHeight);
                    var scrollHeight = Math.max(
                        document.documentElement.scrollHeight,
                        document.body.scrollHeight,
                        document.documentElement.offsetHeight,
                        document.body.offsetHeight,
                        document.documentElement.clientHeight,
                        document.body.clientHeight
                    );
                    var maxHeight = Math.max(contentHeight, scrollHeight);
                    body.style.minHeight = maxHeight + 'px';
                    body.style.height = maxHeight + 'px';
                    body.style.overflow = 'visible';
                    if (html) {
                        html.style.minHeight = maxHeight + 'px';
                        html.style.height = maxHeight + 'px';
                        html.style.overflow = 'visible';
                    }
                    return maxHeight;
                }
                var height = fixHeight();
                return height.toString();
            })();
            """.trimIndent()
        ) { heightStr ->
            val height = heightStr.toIntOrNull() ?: 0
            if (height > 0) {
                val targetHeight = with(density) { (height * density.density).toInt().coerceAtLeast(500).dp }
                webViewHeight = targetHeight
                Timber.d("Fixed WebView height to: $targetHeight")
            }
        }
    }

    LaunchedEffect(navigateToExtract) {
        if (navigateToExtract) {
            val json = viewModel.getExtractedJson()
            if (json != null) {
                Timber.d("Storing JSON in SharedDataStore, length: ${json.length}")
                val success = viewModel.saveToSharedDataStore(json)
                if (success) {
                    Timber.d("JSON saved successfully")
                    onNavigateToExtract()
                } else {
                    Timber.e("Failed to save JSON")
                    snackbarHostState.showSnackbar(localContext.getString(R.string.fetcher_save_failed))
                }
            } else {
                Timber.w("No JSON data available")
            }
            viewModel.onNavigatedToExtract()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.getAndClearSaveResult()?.let { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(parseResult) {
        parseResult?.let {
            try {
                snackbarHostState.showSnackbar(it.asString(localContext))
            } finally {
                viewModel.clearResult()
            }
        }
    }

    if (showHtmlSheet) {
        ModalBottomSheet(
            onDismissRequest = { showHtmlSheet = false },
            sheetState = sheetState,
            dragHandle = null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                val ctx = LocalContext.current
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.fetcher_html_source),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                            tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_copy_html)) } },
                            state = rememberTooltipState()
                        ) {
                            IconButton(
                                onClick = {
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText(ctx.getString(R.string.fetcher_html_source), htmlContent))
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(ctx.getString(R.string.fetcher_html_copied))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.fetcher_copy_html),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    if (currentUrl.isNotBlank()) {
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        )
                    }
                }
                HorizontalDivider()
                SelectionContainer {
                    Text(
                        text = htmlContent.ifEmpty { stringResource(R.string.fetcher_no_html) },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(Route.Fetcher.titleResId),
                subtitle = pageTitle.takeIf { it.isNotBlank() && it != currentUrl && !currentUrl.contains(it) },
                navigationIcon = Icons.Outlined.Close,
                onNavigationClick = { onBack() },
                actions = {
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_toggle_zoom_controls)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { isZoomControlsVisible = !isZoomControlsVisible }) {
                            Icon(
                                imageVector = Icons.Outlined.ZoomIn,
                                contentDescription = stringResource(R.string.fetcher_toggle_zoom_controls)
                            )
                        }
                    }
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Below),
                        tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_view_html)) } },
                        state = rememberTooltipState()
                    ) {
                        IconButton(onClick = { webViewRef.webView?.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                            coroutineScope.launch {
                                val decoded = viewModel.decodeHtml(html)
                                htmlContent = decoded
                                showHtmlSheet = true
                            }
                        } }) {
                            Icon(imageVector = Icons.Outlined.Code, contentDescription = stringResource(R.string.fetcher_view_html))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_extract)) } },
                state = rememberTooltipState()
            ) {
                FloatingActionButton(
                    onClick = {
                        webViewRef.webView?.let { webView ->
                            viewModel.clearResult()
                            webView.evaluateJavascript("(function() { return document.documentElement.outerHTML; })();") { html ->
                                coroutineScope.launch {
                                    val decoded = viewModel.decodeHtml(html)
                                    if (decoded.isNotBlank()) {
                                        viewModel.parseHtml(decoded)
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = fabPadding),
                    shape = AppShapes.small,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Outlined.Description, stringResource(R.string.fetcher_extract), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { loadProgress.toFloat() / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(ProgressIndicatorThinShapes),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
                if (isParsing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(ProgressIndicatorThinShapes),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onSizeChanged { size ->
                            if (webViewHeight == 0.dp && size.height > 0) {
                                webViewHeight = with(density) { size.height.toDp() }
                            }
                        }
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(webViewHeight.coerceAtLeast(500.dp)),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.defaultTextEncodingName = "utf-8"

                                val zhHeaders = mapOf("Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8")

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                        val url = request.url.toString()
                                        if (!url.startsWith("http://") && !url.startsWith("https://")) return true
                                        view.loadUrl(url, zhHeaders)
                                        return true
                                    }

                                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                        isLoading = true
                                        loadProgress = 0
                                    }

                                    override fun onPageFinished(view: WebView, url: String?) {
                                        isLoading = false
                                        loadProgress = 100
                                        url?.let { viewModel.setCurrentUrl(it) }
                                        view.applyPageZoom(zoomPercent)
                                        view.fixBodyHeight()
                                    }

                                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                        if (request.isForMainFrame) isLoading = false
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                                        loadProgress = newProgress
                                        if (newProgress == 100) isLoading = false
                                    }

                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        super.onReceivedTitle(view, title)
                                        title?.let { pageTitle = it }
                                    }
                                }

                                val savedState = webViewState
                                if (savedState != null) {
                                    restoreState(savedState)
                                } else {
                                    loadUrl(currentUrl, zhHeaders)
                                }
                            }.also {
                                webViewRef.webView = it
                            }
                        },
                        update = { }
                    )

                    DisposableEffect(Unit) {
                        onDispose {
                            webViewRef.webView?.let { webView ->
                                val state = Bundle()
                                webView.saveState(state)
                                webViewState = state
                                webView.stopLoading()
                                webView.settings.javaScriptEnabled = false
                                webView.webViewClient = WebViewClient()
                                webView.webChromeClient = WebChromeClient()
                                webView.loadUrl("about:blank")
                                webView.destroy()
                            }
                            webViewRef.webView = null
                        }
                    }

                    if (isZoomControlsVisible) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = if (isLoading && loadProgress in 1..99) 44.dp else 12.dp,
                                    end = 12.dp
                                ),
                            shape = AppShapes.medium,
                            tonalElevation = 4.dp,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_zoom_reset)) } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(onClick = { applyZoom(100) }) {
                                        Text(
                                            text = "$zoomPercent%",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_zoom_out)) } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(
                                        onClick = { applyZoom(zoomPercent - 10) }
                                    ) {
                                        Text(
                                            text = "-",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                                TooltipBox(
                                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                                    tooltip = { PlainTooltip { Text(stringResource(R.string.fetcher_zoom_in)) } },
                                    state = rememberTooltipState()
                                ) {
                                    IconButton(
                                        onClick = { applyZoom(zoomPercent + 10) }
                                    ) {
                                        Text(
                                            text = "+",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (isLoading && loadProgress > 0 && loadProgress < 100) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                BackHandler(enabled = webViewRef.webView?.canGoBack() == true) {
                    webViewRef.webView?.goBack()
                }
            }
        }
    }
}
