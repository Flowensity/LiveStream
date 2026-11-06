package com.livestream.playback.web

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.livestream.core.common.dispatcher.AppDispatchers
import com.livestream.core.model.VideoAspectRatio
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
    private var currentAspectRatio: VideoAspectRatio = VideoAspectRatio.ORIGINAL
    private var loadTimeoutJob: Job? = null
    private var lastStyleInjectTimeMs: Long = 0L
    private val defaultPosterBitmap: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 15000L
        private const val STYLE_INJECT_THROTTLE_MS = 400L
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun loadOfficialUrl(url: String, container: Any?) {
        val viewGroup = container as? ViewGroup ?: return
        if (targetUrl == url && webView != null) {
            if (parentContainer != viewGroup) {
                (webView?.parent as? ViewGroup)?.removeView(webView)
                viewGroup.addView(webView)
                parentContainer = viewGroup
            }
            webView?.alpha = 1f
            applyAspectRatioToWebView()
            injectPlayerStyling(webView)
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

            // 3. 初始设置为透明，防止网页在完成全屏 CSS 注入前闪现居中半屏 (890x500) 容器
            alpha = 0f

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
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

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onVideoReady() {
                        post {
                            if (alpha < 1f) {
                                animate().alpha(1f).setDuration(250).start()
                            }
                            _webState.value = WebPlaybackState.Playing
                        }
                    }
                },
                "LiveStreamBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress in 1..99) {
                        _webState.value = WebPlaybackState.Loading(newProgress)
                    }
                    if (newProgress >= 10) {
                        val now = System.currentTimeMillis()
                        if (now - lastStyleInjectTimeMs >= STYLE_INJECT_THROTTLE_MS) {
                            lastStyleInjectTimeMs = now
                            injectPlayerStyling(view)
                        }
                    }
                }

                override fun getDefaultVideoPoster(): Bitmap? {
                    return defaultPosterBitmap
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
                    if (_webState.value !is WebPlaybackState.Playing) {
                        _webState.value = WebPlaybackState.Loading(95)
                    }
                    injectPlayerStyling(view)

                    // 安全回退：1.5秒后若桥接回调未触发则平滑淡入呈现并标记 Ready，防止黑屏断层
                    controllerScope.launch {
                        delay(1500)
                        post {
                            if (alpha < 1f) {
                                animate().alpha(1f).setDuration(250).start()
                            }
                            if (_webState.value is WebPlaybackState.Loading) {
                                _webState.value = WebPlaybackState.Ready
                            }
                        }
                    }
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
        applyAspectRatioToWebView()

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

    override fun setAspectRatio(aspectRatio: VideoAspectRatio) {
        currentAspectRatio = aspectRatio
        applyAspectRatioToWebView()
        injectPlayerStyling(webView)
    }

    private fun applyAspectRatioToWebView() {
        val wv = webView ?: return
        val container = parentContainer ?: return
        wv.post {
            val parentW = container.width
            val parentH = container.height
            when (currentAspectRatio) {
                VideoAspectRatio.FOUR_THREE -> {
                    if (parentW > 0 && parentH > 0) {
                        val ratio43 = 4f / 3f
                        val containerRatio = parentW.toFloat() / parentH.toFloat()
                        val (targetW, targetH) = if (containerRatio > ratio43) {
                            (parentH * ratio43).toInt() to parentH
                        } else {
                            parentW to (parentW / ratio43).toInt()
                        }
                        wv.layoutParams = FrameLayout.LayoutParams(targetW, targetH, Gravity.CENTER)
                    } else {
                        wv.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                    }
                    wv.scaleX = 1.0f
                    wv.scaleY = 1.0f
                }
                VideoAspectRatio.ZOOM_FILL -> {
                    wv.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    wv.scaleX = 1.22f
                    wv.scaleY = 1.22f
                }
                VideoAspectRatio.SIXTEEN_NINE -> {
                    if (parentW > 0 && parentH > 0) {
                        val ratio169 = 16f / 9f
                        val containerRatio = parentW.toFloat() / parentH.toFloat()
                        val (targetW, targetH) = if (containerRatio > ratio169) {
                            (parentH * ratio169).toInt() to parentH
                        } else {
                            parentW to (parentW / ratio169).toInt()
                        }
                        wv.layoutParams = FrameLayout.LayoutParams(targetW, targetH, Gravity.CENTER)
                    } else {
                        wv.layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            Gravity.CENTER
                        )
                    }
                    wv.scaleX = 1.0f
                    wv.scaleY = 1.0f
                }
                VideoAspectRatio.ORIGINAL -> {
                    wv.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER
                    )
                    wv.scaleX = 1.0f
                    wv.scaleY = 1.0f
                }
            }
            wv.requestLayout()
        }
    }

    private fun injectPlayerStyling(view: WebView?) {
        val objectFit = when (currentAspectRatio) {
            VideoAspectRatio.ORIGINAL -> "contain"
            VideoAspectRatio.SIXTEEN_NINE -> "fill"
            VideoAspectRatio.FOUR_THREE -> "fill"
            VideoAspectRatio.ZOOM_FILL -> "cover"
        }
        val videoTransform = if (currentAspectRatio == VideoAspectRatio.ZOOM_FILL) {
            "scale(1.18)"
        } else {
            "none"
        }
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

                    // 2. 注入全局 CSS：隐藏无关 UI，打破 890PX/500PX/300PX 容器限制，将 video fixed 撑满视口，消除居中半屏
                    var css = 'html, body { width: 100vw !important; height: 100vh !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; background-color: #000000 !important; } ' +
                        '.header, .header-b, header, .max-footer, .footer, footer, .tv-main-con-r, .tv-zhan, .searchMinHeight > div:not(:first-child), .poster, .moren_cover, .xgplayer-controls, xg-controls, .xgplayer-start, xg-start, .xgplayer-poster, .xg-poster { display: none !important; } ' +
                        '*:not(video) { transform: none !important; filter: none !important; perspective: none !important; } ' +
                        '#app, .tv-home, .tv-home-list, .tv, .tv-main, .tv-main-con, .tv-main-con-l, .tv-main-con-l-vid, live-player, .xgplayer, xg-video-container { width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; min-width: 100vw !important; min-height: 100vh !important; margin: 0 !important; padding: 0 !important; background: transparent !important; background-color: transparent !important; border: none !important; } ' +
                        'video { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; object-fit: $objectFit !important; transform: $videoTransform !important; transform-origin: center center !important; background: transparent !important; background-color: transparent !important; margin: 0 !important; padding: 0 !important; border: none !important; z-index: 2147483647 !important; }';

                    function injectCssRule() {
                        var style = document.getElementById('livestream-custom-style');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = 'livestream-custom-style';
                            (document.head || document.documentElement).appendChild(style);
                        }
                        if (style.textContent !== css) {
                            style.textContent = css;
                        }
                    }

                    injectCssRule();

                    function applyVideoFix() {
                        injectCssRule();
                        var vids = document.getElementsByTagName('video');
                        var isReady = false;
                        for (var i = 0; i < vids.length; i++) {
                            var v = vids[i];
                            v.style.setProperty('position', 'fixed', 'important');
                            v.style.setProperty('top', '0px', 'important');
                            v.style.setProperty('left', '0px', 'important');
                            v.style.setProperty('width', '100vw', 'important');
                            v.style.setProperty('height', '100vh', 'important');
                            v.style.setProperty('max-width', '100vw', 'important');
                            v.style.setProperty('max-height', '100vh', 'important');
                            v.style.setProperty('object-fit', '$objectFit', 'important');
                            v.style.setProperty('transform', '$videoTransform', 'important');
                            v.style.setProperty('transform-origin', 'center center', 'important');
                            v.style.setProperty('background-color', 'transparent', 'important');
                            v.style.setProperty('z-index', '2147483647', 'important');
                            v.style.setProperty('margin', '0px', 'important');
                            v.style.setProperty('padding', '0px', 'important');
                            if (!v.__ls_bound) {
                                v.__ls_bound = true;
                                v.addEventListener('playing', function() {
                                    if (window.LiveStreamBridge && window.LiveStreamBridge.onVideoReady) {
                                        window.LiveStreamBridge.onVideoReady();
                                    }
                                });
                                v.addEventListener('loadeddata', function() {
                                    if (window.LiveStreamBridge && window.LiveStreamBridge.onVideoReady) {
                                        window.LiveStreamBridge.onVideoReady();
                                    }
                                });
                            }
                            if (v.paused) {
                                v.muted = false;
                                v.play().catch(function(){});
                            }
                            if (v.videoWidth > 0 || v.readyState >= 2 || v.currentTime > 0) {
                                isReady = true;
                            }
                        }
                        var covers = document.querySelectorAll('.poster, .moren_cover, .xgplayer-poster, .xg-poster, .xgplayer-start, xg-start');
                        for (var j = 0; j < covers.length; j++) {
                            covers[j].style.setProperty('display', 'none', 'important');
                        }
                        if (isReady && window.LiveStreamBridge && window.LiveStreamBridge.onVideoReady) {
                            window.LiveStreamBridge.onVideoReady();
                        }
                    }

                    applyVideoFix();

                    if (!window.__livestream_observer && window.MutationObserver) {
                        window.__livestream_observer = new MutationObserver(function() {
                            applyVideoFix();
                        });
                        var target = document.documentElement || document.body;
                        if (target) {
                            window.__livestream_observer.observe(target, {
                                childList: true,
                                subtree: true
                            });
                        }
                    }

                    window.dispatchEvent(new Event('resize'));

                    if (window.__livestream_style_timer) {
                        clearInterval(window.__livestream_style_timer);
                    }
                    var count = 0;
                    window.__livestream_style_timer = setInterval(function() {
                        applyVideoFix();
                        window.dispatchEvent(new Event('resize'));
                        count++;
                        if (count >= 20) {
                            clearInterval(window.__livestream_style_timer);
                            window.__livestream_style_timer = null;
                        }
                    }, 250);
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    override fun pause() {
        webView?.let { wv ->
            wv.evaluateJavascript(
                "(function(){try{var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){v[i].pause();}}catch(e){}})();",
                null
            )
            wv.onPause()
        }
    }

    override fun resume() {
        webView?.let { wv ->
            wv.onResume()
            wv.evaluateJavascript(
                "(function(){try{var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){v[i].play().catch(function(){});}}catch(e){}})();",
                null
            )
        }
    }

    override fun detachContainer(container: Any?) {
        val viewGroup = container as? ViewGroup
        if (viewGroup == null || parentContainer == viewGroup) {
            (webView?.parent as? ViewGroup)?.removeView(webView)
            parentContainer = null
        }
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
