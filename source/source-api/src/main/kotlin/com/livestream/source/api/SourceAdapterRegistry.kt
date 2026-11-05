package com.livestream.source.api

/**
 * 适配器注册中心契约，支持根据 providerId 动态分发路由
 */
interface SourceAdapterRegistry {

    fun register(adapter: SourceAdapter)

    fun unregister(providerId: String)

    fun getAdapter(providerId: String): SourceAdapter?

    fun getAllAdapters(): List<SourceAdapter>
}

class DefaultSourceAdapterRegistry : SourceAdapterRegistry {

    private val adapters = mutableMapOf<String, SourceAdapter>()

    override fun register(adapter: SourceAdapter) {
        adapters[adapter.providerId] = adapter
    }

    override fun unregister(providerId: String) {
        adapters.remove(providerId)
    }

    override fun getAdapter(providerId: String): SourceAdapter? {
        return adapters[providerId]
    }

    override fun getAllAdapters(): List<SourceAdapter> {
        return adapters.values.toList()
    }
}
