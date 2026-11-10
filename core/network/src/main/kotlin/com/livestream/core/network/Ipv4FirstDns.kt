package com.livestream.core.network

import okhttp3.Dns
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * 针对 Android TV / 机顶盒网络环境优化的 DNS 解析器：
 * 1. IPv4 优先排序（Happy Eyeballs 简化策略），规避部分家庭宽带 IPv6 路由黑洞导致的 3~5 秒建连超时；
 * 2. 内存级 TTL 缓存，消除同域名频道切换与分片拉取时的重复 DNS 解析耗时。
 */
class Ipv4FirstDns(
    private val delegate: Dns = Dns.SYSTEM,
    private val cacheTtlMs: Long = 300_000L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : Dns {

    private data class CacheEntry(
        val addresses: List<InetAddress>,
        val expireAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun lookup(hostname: String): List<InetAddress> {
        val now = clock()
        val cached = cache[hostname]
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
            cache[hostname] = CacheEntry(
                addresses = resolved,
                expireAtMs = now + cacheTtlMs
            )
        }
        return resolved
    }
}
