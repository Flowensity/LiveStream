package com.livestream.playback.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.playback.api.web.WebPlaybackController
import com.livestream.playback.api.web.WebPlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultWebPlaybackController @Inject constructor(
    private val dispatchers: AppDispatchers
) : WebPlaybackController {

    private val controllerScope = CoroutineScope(SupervisorJob() + dispatchers.main)

    private val _webState = MutableStateFlow<WebPlaybackState>(WebPlaybackState.Idle)
    override val webState: StateFlow<WebPlaybackState> = _webState.asStateFlow()

    private val _activeWebViewCount = MutableStateFlow(0)
    val activeWebViewCount: StateFlow<Int> = _activeWebViewCount.asStateFlow()

    private var webView: WebView? = null
    private var targetUrl: String? = null
    private var parentContainer: ViewGroup? = null
    private var loadTimeoutJob: Job? = null

    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 15000L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun loadOfficialUrl(url: String, container: Any?) {
        stopAndDestroy()

        val viewGroup = container as? ViewGroup ?: return
        targetUrl = url
        parentContainer = viewGroup

        val context = viewGroup.context
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _webState.value = WebPlaybackState.Loading(0)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loadTimeoutJob?.cancel()
                    _webState.value = WebPlaybackState.Ready
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        loadTimeoutJob?.cancel()
                        val desc = error?.description?.toString() ?: "网页加载异常"
                        _webState.value = WebPlaybackState.Error(
                            code = error?.errorCode ?: -1,
                            description = desc
                        )
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    // Renderer 崩溃隔离恢复处理，防止整个宿主应用崩溃闪退
                    (view?.parent as? ViewGroup)?.removeView(view)
                    view?.destroy()
                    webView = null
                    loadTimeoutJob?.cancel()
                    _webState.value = WebPlaybackState.RendererCrashed
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress in 1..99) {
                        _webState.value = WebPlaybackState.Loading(newProgress)
                    }
                }
            }
        }

        container.addView(wv)
        webView = wv
        _activeWebViewCount.value = 1

        // 超时保护定时器
        loadTimeoutJob = controllerScope.launch {
            delay(PAGE_LOAD_TIMEOUT_MS)
            if (_webState.value is WebPlaybackState.Loading) {
                wv.stopLoading()
                _webState.value = WebPlaybackState.Error(
                    code = -1,
                    description = "官方网页加载超时 (${PAGE_LOAD_TIMEOUT_MS / 1000}秒)"
                )
            }
        }

        wv.loadUrl(url)
    }

    override fun reload() {
        val url = targetUrl
        val container = parentContainer
        if (url != null && container != null) {
            loadOfficialUrl(url, container)
        } else {
            webView?.reload()
        }
    }

    override fun stopAndDestroy() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null

        webView?.let { wv ->
            wv.stopLoading()
            wv.clearHistory()
            wv.loadUrl("about:blank")
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webView = null
        _activeWebViewCount.value = 0
        _webState.value = WebPlaybackState.Destroyed
    }
}
