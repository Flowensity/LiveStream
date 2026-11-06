package com.livestream.data.epg.service

import com.livestream.core.common.dispatcher.AppDispatchers
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CntvEpgServiceTest {

    private lateinit var service: CntvEpgService

    @Before
    fun setUp() {
        val dispatchers = object : AppDispatchers {
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
            override val main = Dispatchers.Unconfined
        }
        service = CntvEpgService(
            okHttpClient = OkHttpClient(),
            dispatchers = dispatchers
        )
    }

    @Test
    fun `resolveCntvChannelCode matches standard and suffixed CCTV channels`() {
        assertEquals("cctv1", service.resolveCntvChannelCode("CCTV-1"))
        assertEquals("cctv1", service.resolveCntvChannelCode("CCTV1"))
        assertEquals("cctv1", service.resolveCntvChannelCode("CCTV-1 高清"))
        assertEquals("cctv1", service.resolveCntvChannelCode("CCTV1 HD"))
        assertEquals("cctv1", service.resolveCntvChannelCode("CCTV-1 综合"))

        assertEquals("cctv2", service.resolveCntvChannelCode("CCTV-2 财经"))
        assertEquals("cctv3", service.resolveCntvChannelCode("CCTV-3 综艺 高清"))
        assertEquals("cctv4", service.resolveCntvChannelCode("CCTV-4 中文国际"))
        assertEquals("cctv5", service.resolveCntvChannelCode("CCTV-5 体育"))
        assertEquals("cctv5plus", service.resolveCntvChannelCode("CCTV-5+"))
        assertEquals("cctv5plus", service.resolveCntvChannelCode("CCTV5+ 4K"))
        assertEquals("cctv5plus", service.resolveCntvChannelCode("CCTV5+ 体育赛事"))

        assertEquals("cctvjilu", service.resolveCntvChannelCode("CCTV-9"))
        assertEquals("cctvjilu", service.resolveCntvChannelCode("CCTV-9 纪录"))
        assertEquals("cctvchild", service.resolveCntvChannelCode("CCTV-14 少儿"))
        assertEquals("cctv16", service.resolveCntvChannelCode("CCTV-16 4K超高清"))
        assertEquals("cctv16", service.resolveCntvChannelCode("CCTV16 奥林匹克"))
        assertEquals("cctv17", service.resolveCntvChannelCode("CCTV-17 农业农村"))
    }

    @Test
    fun `resolveCntvChannelCode matches dedicated 4K and 8K channels`() {
        assertEquals("cctv4k", service.resolveCntvChannelCode("CCTV-4K"))
        assertEquals("cctv4k", service.resolveCntvChannelCode("CCTV 4K 超高清"))
        assertEquals("cctv8k", service.resolveCntvChannelCode("CCTV-8K"))
    }

    @Test
    fun `resolveCntvChannelCode matches Chinese numeral aliases`() {
        assertEquals("cctv1", service.resolveCntvChannelCode("中央一台"))
        assertEquals("cctv5", service.resolveCntvChannelCode("中央五台"))
        assertEquals("cctv13", service.resolveCntvChannelCode("中央十三台"))
        assertEquals("cctv16", service.resolveCntvChannelCode("中央十六台"))
        assertEquals("cctvchild", service.resolveCntvChannelCode("中央少儿"))
    }

    @Test
    fun `resolveCntvChannelCode matches provincial satellite channels with suffixes`() {
        assertEquals("btv1", service.resolveCntvChannelCode("北京卫视"))
        assertEquals("btv1", service.resolveCntvChannelCode("北京卫视 高清"))
        assertEquals("btv1", service.resolveCntvChannelCode("北京卫视 [1080P]"))
        assertEquals("hunan", service.resolveCntvChannelCode("湖南卫视 HD"))
        assertEquals("dongfang", service.resolveCntvChannelCode("东方卫视 4K"))
        assertEquals("zhejiang", service.resolveCntvChannelCode("浙江卫视"))
        assertEquals("jiangsu", service.resolveCntvChannelCode("江苏卫视高清"))
        assertEquals("guangdong", service.resolveCntvChannelCode("广东卫视 4K超高清"))
    }

    @Test
    fun `resolveCntvChannelCode returns null for unrelated channels`() {
        assertNull(service.resolveCntvChannelCode(""))
        assertNull(service.resolveCntvChannelCode("   "))
        assertNull(service.resolveCntvChannelCode("北京少儿"))
        assertNull(service.resolveCntvChannelCode("未知测试频道"))
    }
}
