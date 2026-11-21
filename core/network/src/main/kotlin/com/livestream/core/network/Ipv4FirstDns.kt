package com.livestream.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.Collections

/**
 * 针对 Android TV / 机顶盒网络环境优化的 DNS 解析器：
 * 1. IPv4 优先排序（Happy Eyeballs 简化策略），规避部分家庭宽带 IPv6 路由黑洞导致的 3~5 秒建连超时；
 * 2. 有界内存级 TTL 缓存，消除同域名频道切换与分片拉取时的重复 DNS 解析耗时并防止缓存无限增长。
 */
class Ipv4FirstDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val cacheTtlMs: Long = 300_000L,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val maxEntries: Int = 256
) : Dns {

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expireAtMs: Long
    )

    private val cache: MutableMap<String, CacheEntry> = Collections.synchronizedMap(
        object : LinkedHashMap<String, CacheEntry>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
                return size > maxEntries.coerceAtLeast(1)
            }
        }
    )

    internal fun cacheSizeForTest(): Int = synchronized(cache) { cache.size }

    override fun lookup(hostname: String): List<InetAddress> {
        val now = clock()
        val cached = synchronized(cache) { cache[hostname] }
        if (cached != null && now < cached.expireAtMs) {
            return cached.addresses
        }

        val resolved = try {
            delegate.lookup(hostname).sortedBy { addr ->
                if (addr is Inet4Address) 0 else 1
            }
        } catch (e: Exception) {
            // 若刷新失败但仍有旧缓存，兜底返回旧缓存以提升弱网可用性
            if (cached != null) return cached.addresses
            throw e
        }

        if (resolved.isNotEmpty()) {
            synchronized(cache) {
                cache[hostname] = CacheEntry(
                    addresses = resolved,
                    expireAtMs = now + cacheTtlMs
                )
            }
        }
        return resolved
    }
}
