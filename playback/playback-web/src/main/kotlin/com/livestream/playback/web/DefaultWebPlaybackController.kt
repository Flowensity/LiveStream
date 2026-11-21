package com.livestream.playback.web

import android.annotation.SuppressLint
import android.content.Context
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
import android.webkit.WebResourceResponse
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
import java.io.ByteArrayInputStream
import java.util.Locale
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
    private var pageFinishedFallbackJob: Job? = null
    private var lastStyleInjectTimeMs: Long = 0L
    private var isUserPaused: Boolean = false
    private val defaultPosterBitmap: Bitmap by lazy {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    companion object {
        private const val PAGE_LOAD_TIMEOUT_MS = 12000L
        private const val STYLE_INJECT_THROTTLE_MS = 250L
        private val BLOCKED_HOST_KEYWORDS = arrayOf(
            "hm.baidu.com",
            "beacon.qq.com",
            "trace.qq.com",
            "btrace.qq.com",
            "btrace.yangshipin.cn",
            "aatc-api.yangshipin.cn",
            "pcsite.ysp.cctv.cn",
            "mediadata.xuexi.cn",
            "o.alicdn.com/qtsdk",
            "api4.ipify.org",
            "report.url.cn",
            "google-analytics.com",
            "googletagmanager.com",
            "sensorsdata"
        )
        private val BLOCKED_PATH_KEYWORDS = arrayOf(
            "vconsole.min.js",
            "/static/project/test/player/vr/",
            "/voapi/omsot/servertime",
            "/api/yspepg/program/",
            "/api/oms/pc/config"
        )
        private val BLOCKED_EXTENSIONS = arrayOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".ico", ".bmp",
            ".woff", ".woff2", ".ttf", ".otf", ".eot"
        )
        private val ALLOWED_STREAM_EXTENSIONS = arrayOf(
            ".m3u8", ".ts", ".flv", ".mp4", ".mpd", ".m4s", ".aac"
        )
    }

    internal fun shouldBlockWebResource(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase(Locale.ROOT)
        val pathPart = lower.substringBefore('?').substringBefore('#')
        for (streamExt in ALLOWED_STREAM_EXTENSIONS) {
            if (pathPart.endsWith(streamExt)) return false
        }
        for (hostKeyword in BLOCKED_HOST_KEYWORDS) {
            if (lower.contains(hostKeyword)) return true
        }
        for (pathKeyword in BLOCKED_PATH_KEYWORDS) {
            if (lower.contains(pathKeyword)) return true
        }
        for (ext in BLOCKED_EXTENSIONS) {
            if (pathPart.endsWith(ext)) return true
        }
        if (lower.contains("imagemogr2/format/")) return true
        return false
    }

    internal fun extractYangshipinPid(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val lower = url.lowercase(Locale.ROOT)
        if (!lower.contains("yangshipin.cn/tv/home")) return null
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (param in query.split('&')) {
            val key = param.substringBefore('=')
            val value = param.substringAfter('=', "").substringBefore('#')
            if (key.equals("pid", ignoreCase = true) && value.isNotBlank() && value.all { it.isDigit() }) {
                return value
            }
        }
        return null
    }

    override fun loadOfficialUrl(url: String, container: Any?) {
        loadOfficialUrlInternal(url, container, forceReload = false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun loadOfficialUrlInternal(url: String, container: Any?, forceReload: Boolean) {
        val viewGroup = (container as? ViewGroup) ?: parentContainer
        isUserPaused = false
        val existingWv = webView
        if (existingWv != null && canReusePooledInstance()) {
            if (viewGroup != null && parentContainer != viewGroup) {
                (existingWv.parent as? ViewGroup)?.removeView(existingWv)
                viewGroup.addView(existingWv)
                parentContainer = viewGroup
            }
            if (!forceReload && targetUrl == url) {
                existingWv.animate().cancel()
                if (_webState.value is WebPlaybackState.Playing || _webState.value is WebPlaybackState.Ready) {
                    existingWv.alpha = 1f
                }
                applyAspectRatioToWebView()
                injectPlayerStyling(existingWv)
                return
            }
            // 实例池化热复用：不同官方网页切换或重连时直接复用已初始化的 Chromium WebView 实例
            loadTimeoutJob?.cancel()
            pageFinishedFallbackJob?.cancel()
            val prevUrl = targetUrl
            targetUrl = url
            _webState.value = WebPlaybackState.Loading(0)
            applyAspectRatioToWebView()
            startLoadTimeout(existingWv)

            val prevPid = extractYangshipinPid(prevUrl)
            val nextPid = extractYangshipinPid(url)
            if (prevPid != null && nextPid != null) {
                existingWv.evaluateJavascript(buildInPageChannelSwitchScript(nextPid)) { result ->
                    if (result != null && result.contains("OK")) {
                        injectPlayerStyling(existingWv)
                        pageFinishedFallbackJob?.cancel()
                        pageFinishedFallbackJob = controllerScope.launch {
                            delay(1200)
                            existingWv.post {
                                existingWv.animate().cancel()
                                existingWv.alpha = 1f
                                if (_webState.value is WebPlaybackState.Loading) {
                                    _webState.value = WebPlaybackState.Ready
                                }
                            }
                        }
                    } else {
                        existingWv.stopLoading()
                        existingWv.animate().cancel()
                        existingWv.alpha = 0f
                        existingWv.loadUrl(url)
                    }
                }
                return
            }

            existingWv.stopLoading()
            // 先暂停并清空旧 video 元素，防止旧画面残留或误触发新频道的 onVideoReady
            existingWv.evaluateJavascript(
                "(function(){try{var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){v[i].pause();v[i].removeAttribute('src');v[i].load();}}catch(e){}})();",
                null
            )
            existingWv.animate().cancel()
            existingWv.alpha = 0f
            existingWv.loadUrl(url)
            return
        }

        if (existingWv == null && canReusePooledInstance()) {
            if (viewGroup != null) {
                parentContainer = viewGroup
            }
            if (forceReload || targetUrl != url) {
                targetUrl = url
                _webState.value = WebPlaybackState.Loading(0)
            }
            return
        }

        if (viewGroup == null) {
            return
        }

        stopAndDestroy()

        targetUrl = url
        parentContainer = viewGroup

        val context = resolveWebViewContext(viewGroup.context)
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
                cacheMode = WebSettings.LOAD_DEFAULT
                // 禁用网页内无关网络图片加载（全屏仅展示 <video>），大幅释放带宽给视频流与核心 JS
                blockNetworkImage = true
                loadsImagesAutomatically = false
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
                            loadTimeoutJob?.cancel()
                            pageFinishedFallbackJob?.cancel()
                            animate().cancel()
                            alpha = 1f
                            _webState.value = WebPlaybackState.Playing
                        }
                    }
                },
                "LiveStreamBridge"
            )

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress in 1..99 && _webState.value !is WebPlaybackState.Playing) {
                        _webState.value = WebPlaybackState.Loading(newProgress)
                    }
                    if (newProgress >= 5) {
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
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    if (request != null && !request.isForMainFrame) {
                        val reqUrl = request.url?.toString().orEmpty()
                        if (shouldBlockWebResource(reqUrl)) {
                            return WebResourceResponse(
                                "text/plain",
                                "UTF-8",
                                ByteArrayInputStream(ByteArray(0))
                            )
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    _webState.value = WebPlaybackState.Loading(0)
                    injectPlayerStyling(view)
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    injectPlayerStyling(view)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    loadTimeoutJob?.cancel()
                    if (_webState.value !is WebPlaybackState.Playing) {
                        _webState.value = WebPlaybackState.Loading(95)
                    }
                    injectPlayerStyling(view)

                    // 极速回退：250ms 后若桥接回调未触发则立即呈现并标记 Ready，消除 1.5s+250ms 的人为等待
                    pageFinishedFallbackJob?.cancel()
                    pageFinishedFallbackJob = controllerScope.launch {
                        delay(250)
                        post {
                            animate().cancel()
                            alpha = 1f
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

        viewGroup?.addView(wv)
        webView = wv
        _activeWebViewCount.value = 1
        _webState.value = WebPlaybackState.Loading(0)
        applyAspectRatioToWebView()

        startLoadTimeout(wv)
        wv.loadUrl(url)
    }

    private fun startLoadTimeout(wv: WebView) {
        loadTimeoutJob?.cancel()
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
    }

    internal fun resolveWebViewContext(containerContext: Context): Context {
        return containerContext.applicationContext ?: containerContext
    }

    internal fun canReusePooledInstance(): Boolean {
        return _activeWebViewCount.value > 0 &&
            _webState.value !is WebPlaybackState.Destroyed &&
            _webState.value !is WebPlaybackState.RendererCrashed
    }

    internal fun markPooledInstanceReadyForTest(url: String, container: ViewGroup? = null) {
        targetUrl = url
        parentContainer = container
        _activeWebViewCount.value = 1
        _webState.value = WebPlaybackState.Ready
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
        if (view == null) return
        view.evaluateJavascript(buildPlayerStylingScript(currentAspectRatio, isUserPaused), null)
    }

    internal fun buildInPageChannelSwitchScript(targetPid: String): String {
        return """
            (function() {
                try {
                    var appEl = document.getElementById('app');
                    var rootVm = appEl && appEl.__vue__;
                    if (!rootVm) return 'FALLBACK';
                    function findTvVm(vm) {
                        if (!vm) return null;
                        if (vm.tvTabs && vm.${'$'}options && vm.${'$'}options.methods && vm.${'$'}options.methods.changeTV) {
                            return vm;
                        }
                        var children = vm.${'$'}children || [];
                        for (var i = 0; i < children.length; i++) {
                            var found = findTvVm(children[i]);
                            if (found) return found;
                        }
                        return null;
                    }
                    var vm = findTvVm(rootVm);
                    if (!vm || !Array.isArray(vm.tvTabs)) return 'FALLBACK';
                    var target = null;
                    var targetTabIdx = 0;
                    for (var ti = 0; ti < vm.tvTabs.length; ti++) {
                        var tab = vm.tvTabs[ti] || [];
                        for (var ci = 0; ci < tab.length; ci++) {
                            if (String(tab[ci].pid) === '$targetPid') {
                                target = tab[ci];
                                targetTabIdx = ti;
                                break;
                            }
                        }
                        if (target) break;
                    }
                    if (!target) return 'FALLBACK';

                    var vids = document.getElementsByTagName('video');
                    for (var vi = 0; vi < vids.length; vi++) {
                        try {
                            vids[vi].pause();
                            vids[vi].removeAttribute('src');
                            vids[vi].load();
                        } catch (e) {}
                    }

                    vm.setTvConfig = function(p, s, c, vr, pt) {
                        this.videoConfig = {
                            pid: p,
                            vid: s,
                            poster: c,
                            isTV: true,
                            autoplay: true,
                            viewRights: vr,
                            payType: pt
                        };
                    };
                    vm.getInit = function() {};
                    vm.currentIndex = targetTabIdx;
                    vm.selectIndex = targetTabIdx;
                    vm.tvIndex = target;
                    if (vm.${'$'}router && vm.${'$'}route) {
                        try {
                            var nextQuery = Object.assign({}, vm.${'$'}route.query, { pid: target.pid });
                            vm.${'$'}router.replace({ query: nextQuery }, function(){}, function(){});
                        } catch (e) {}
                    }
                    vm.videoConfig = {};
                    vm.${'$'}nextTick(function() {
                        vm.setTvConfig(target.pid, target.streamId, target.coverUrl, target.viewRights, target.payType, target);
                    });
                    return 'OK';
                } catch (e) {
                    return 'FALLBACK';
                }
            })();
        """.trimIndent()
    }

    internal fun buildPlayerStylingScript(
        aspectRatio: VideoAspectRatio,
        isPaused: Boolean
    ): String {
        val objectFit = when (aspectRatio) {
            VideoAspectRatio.ORIGINAL -> "contain"
            VideoAspectRatio.SIXTEEN_NINE -> "fill"
            VideoAspectRatio.FOUR_THREE -> "fill"
            VideoAspectRatio.ZOOM_FILL -> "cover"
        }
        val videoTransform = if (aspectRatio == VideoAspectRatio.ZOOM_FILL) {
            "scale(1.18)"
        } else {
            "none"
        }
        return """
            (function() {
                try {
                    window.__livestream_paused = $isPaused;

                    if (!window.__ls_timeout_hooked) {
                        window.__ls_timeout_hooked = true;
                        var origSetTimeout = window.setTimeout;
                        window.setTimeout = function(fn, delay) {
                            if (delay === 300 && typeof fn === 'function') {
                                try {
                                    if (Function.prototype.toString.call(fn).indexOf('videoConfig') !== -1) {
                                        return origSetTimeout(fn, 0);
                                    }
                                } catch (e) {}
                            }
                            return origSetTimeout.apply(this, arguments);
                        };
                    }

                    if (window['cctvh5-openapi'] && window['cctvh5-openapi'].getSignature && !window.__ls_openapi_warmed) {
                        window.__ls_openapi_warmed = true;
                        try {
                            window['cctvh5-openapi'].getSignature({ yspappid: '519748109' });
                        } catch (e) {}
                    }

                    var prefetches = document.querySelectorAll ? document.querySelectorAll('link[rel="prefetch"]') : [];
                    for (var pi = 0; pi < prefetches.length; pi++) {
                        if (prefetches[pi].parentNode) {
                            prefetches[pi].parentNode.removeChild(prefetches[pi]);
                        }
                    }

                    var appEl = document.getElementById('app');
                    var rootVm = appEl && appEl.__vue__;
                    if (rootVm) {
                        (function accelerateVue(vm) {
                            if (!vm) return;
                            if (vm.${'$'}options && vm.${'$'}options.name === 'tv-home' && !vm.__ls_feed_booted) {
                                vm.__ls_feed_booted = true;
                                var origInit = vm.init;
                                if (!vm.currentTab && typeof origInit === 'function') {
                                    vm.currentTab = 'PG00000004';
                                    vm.init = function() {};
                                    origInit.call(vm, 'PG00000004');
                                } else {
                                    vm.init = function() {};
                                }
                            }
                            if (vm.tvTabs && vm.${'$'}options && vm.${'$'}options.methods && vm.${'$'}options.methods.changeTV && !vm.__ls_tv_patched) {
                                vm.__ls_tv_patched = true;
                                vm.setTvConfig = function(p, s, c, vr, pt) {
                                    this.videoConfig = {
                                        pid: p,
                                        vid: s,
                                        poster: c,
                                        isTV: true,
                                        autoplay: true,
                                        viewRights: vr,
                                        payType: pt
                                    };
                                };
                                vm.getInit = function() {};
                                if (vm.tvIndex && vm.tvIndex.pid && (!vm.videoConfig || !vm.videoConfig.vid)) {
                                    vm.setTvConfig(vm.tvIndex.pid, vm.tvIndex.streamId, vm.tvIndex.coverUrl, vm.tvIndex.viewRights, vm.tvIndex.payType, vm.tvIndex);
                                }
                            }
                            var children = vm.${'$'}children || [];
                            for (var ci = 0; ci < children.length; ci++) {
                                accelerateVue(children[ci]);
                            }
                        })(rootVm);
                    }

                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

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
                                var notifyReady = function() {
                                    if (window.LiveStreamBridge && window.LiveStreamBridge.onVideoReady) {
                                        window.LiveStreamBridge.onVideoReady();
                                    }
                                };
                                v.addEventListener('loadedmetadata', notifyReady);
                                v.addEventListener('loadeddata', notifyReady);
                                v.addEventListener('canplay', notifyReady);
                                v.addEventListener('playing', notifyReady);
                                v.addEventListener('timeupdate', notifyReady, { once: true });
                            }
                            if (v.paused && !window.__livestream_paused) {
                                v.muted = false;
                                v.play().catch(function(){});
                            }
                            if (v.videoWidth > 0 || v.readyState >= 1 || v.currentTime > 0) {
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
                            if (!window.__ls_raf_pending) {
                                window.__ls_raf_pending = true;
                                var schedule = window.requestAnimationFrame || function(cb) { setTimeout(cb, 16); };
                                schedule(function() {
                                    window.__ls_raf_pending = false;
                                    applyVideoFix();
                                });
                            }
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

                    window.__livestream_style_count = 0;
                    if (!window.__livestream_style_timer) {
                        window.__livestream_style_timer = setInterval(function() {
                            applyVideoFix();
                            window.dispatchEvent(new Event('resize'));
                            window.__livestream_style_count = (window.__livestream_style_count || 0) + 1;
                            if (window.__livestream_style_count >= 15) {
                                clearInterval(window.__livestream_style_timer);
                                window.__livestream_style_timer = null;
                            }
                        }, 200);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
    }

    override fun pause() {
        isUserPaused = true
        webView?.let { wv ->
            wv.evaluateJavascript(
                "(function(){try{window.__livestream_paused=true;var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){v[i].pause();}}catch(e){}})();",
                null
            )
            wv.onPause()
        }
    }

    override fun resume() {
        isUserPaused = false
        webView?.let { wv ->
            wv.onResume()
            wv.evaluateJavascript(
                "(function(){try{window.__livestream_paused=false;var v=document.getElementsByTagName('video');for(var i=0;i<v.length;i++){v[i].play().catch(function(){});}}catch(e){}})();",
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
        if (url != null) {
            loadOfficialUrlInternal(url, container, forceReload = true)
        } else if (webView != null) {
            isUserPaused = false
            _webState.value = WebPlaybackState.Loading(0)
            startLoadTimeout(webView!!)
            webView?.reload()
        }
    }

    override fun stopAndDestroy() {
        loadTimeoutJob?.cancel()
        loadTimeoutJob = null
        pageFinishedFallbackJob?.cancel()
        pageFinishedFallbackJob = null

        webView?.let { wv ->
            wv.stopLoading()
            wv.removeJavascriptInterface("LiveStreamBridge")
            wv.clearHistory()
            wv.loadUrl("about:blank")
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webView = null
        parentContainer = null
        targetUrl = null
        _activeWebViewCount.value = 0
        _webState.value = WebPlaybackState.Destroyed
    }
}
