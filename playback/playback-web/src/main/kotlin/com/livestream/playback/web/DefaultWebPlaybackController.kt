package com.livestream.playback.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.View
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
        val viewGroup = container as? ViewGroup ?: return
        if (targetUrl == url && webView != null && parentContainer == viewGroup) {
            return
        }

        stopAndDestroy()

        targetUrl = url
        parentContainer = viewGroup

        val context = viewGroup.context
        val wv = WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // 1. 硬件加速渲染图层与透明背景设置，确保 MSE 视频纹理能正常合成并可见
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(0)

            // 2. 避免 WebView 抢夺 Android TV 遥控器与界面输入焦点
            isFocusable = false
            isFocusableInTouchMode = false
            clearFocus()

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress in 1..99) {
                        _webState.value = WebPlaybackState.Loading(newProgress)
                    }
                    if (newProgress >= 50) {
                        injectPlayerStyling(view)
                    }
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _webState.value = WebPlaybackState.Loading(0)
                    injectPlayerStyling(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loadTimeoutJob?.cancel()
                    _webState.value = WebPlaybackState.Ready
                    injectPlayerStyling(view)
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
        }

        viewGroup.addView(wv)
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

    private fun injectPlayerStyling(view: WebView?) {
        val js = """
            (function() {
                try {
                    // 1. 确保 viewport meta 适应当前移动/电视屏幕
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                    // 2. 注入全局 CSS：隐藏无关 UI，打破 890PX/500PX/300PX 容器限制，将 video fixed 撑满视口
                    var css = 'html, body { width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background-color: #000000 !important; } ' +
                        '.header, .header-b, header, .max-footer, .footer, footer, .tv-main-con-r, .tv-zhan, .searchMinHeight > div:not(:first-child), .poster, .moren_cover, .xgplayer-controls, xg-controls, .xgplayer-start, xg-start, .xgplayer-poster, .xg-poster { display: none !important; } ' +
                        '* { transform: none !important; filter: none !important; perspective: none !important; } ' +
                        '#app, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l, .tv-main-con-l-vid, live-player, .xgplayer, xg-video-container { width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; min-width: 100vw !important; min-height: 100vh !important; margin: 0 !important; padding: 0 !important; background: transparent !important; background-color: transparent !important; border: none !important; } ' +
                        'video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; object-fit: contain !important; background: transparent !important; background-color: transparent !important; margin: 0 !important; padding: 0 !important; border: none !important; z-index: 2147483647 !important; }';

                    var style = document.getElementById('livestream-custom-style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'livestream-custom-style';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.innerHTML = css;

                    function applyVideoFix() {
                        var vids = document.getElementsByTagName('video');
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            v.style.setProperty('position', 'fixed', 'important');
                            v.style.setProperty('top', '0px', 'important');
                            v.style.setProperty('left', '0px', 'important');
                            v.style.setProperty('width', '100vw', 'important');
                            v.style.setProperty('height', '100vh', 'important');
                            v.style.setProperty('max-width', '100vw', 'important');
                            v.style.setProperty('max-height', '100vh', 'important');
                            v.style.setProperty('object-fit', 'contain', 'important');
                            v.style.setProperty('background-color', 'transparent', 'important');
                            v.style.setProperty('z-index', '2147483647', 'important');
                            v.style.setProperty('margin', '0px', 'important');
                            v.style.setProperty('padding', '0px', 'important');
                            if (v.paused) {
                                v.muted = false;
                                v.play().catch(function(){});
                            }
                        }
                        var covers = document.querySelectorAll('.poster, .moren_cover, .xgplayer-poster, .xg-poster, .xgplayer-start, xg-start');
                        for (var j = 0; j < covers.length; j++) {
                            covers[j].style.setProperty('display', 'none', 'important');
                        }
                    }

                    applyVideoFix();
                    var count = 0;
                    var timer = setInterval(function() {
                        applyVideoFix();
                        count++;
                        if (count >= 25) {
                            clearInterval(timer);
                        }
                    }, 350);
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
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
