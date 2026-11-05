package com.livestream.data.channel.preset

import com.livestream.core.model.Channel
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup

/**
 * 预置真实频道元数据定义（不硬编码伪造播放地址，仅提供真实规范的频道与分类结构）
 */
object PresetChannelDataProvider {

    val categories = listOf(
        ChannelCategory(
            id = "cctv",
            name = "央视频道",
            group = ChannelCategoryGroup.CCTV,
            orderIndex = 1
        ),
        ChannelCategory(
            id = "satellite",
            name = "各省卫视",
            group = ChannelCategoryGroup.SATELLITE,
            orderIndex = 2
        ),
        ChannelCategory(
            id = "local",
            name = "地方电视台",
            group = ChannelCategoryGroup.LOCAL,
            orderIndex = 3
        ),
        ChannelCategory(
            id = "custom",
            name = "自定义源",
            group = ChannelCategoryGroup.CUSTOM,
            orderIndex = 4
        )
    )

    val cctvChannels = listOf(
        Channel(id = "cctv1", name = "CCTV-1 综合", channelNumber = 1, categoryId = "cctv", orderIndex = 1),
        Channel(id = "cctv2", name = "CCTV-2 财经", channelNumber = 2, categoryId = "cctv", orderIndex = 2),
        Channel(id = "cctv3", name = "CCTV-3 综艺", channelNumber = 3, categoryId = "cctv", orderIndex = 3),
        Channel(id = "cctv4", name = "CCTV-4 中文国际", channelNumber = 4, categoryId = "cctv", orderIndex = 4),
        Channel(id = "cctv5", name = "CCTV-5 体育", channelNumber = 5, categoryId = "cctv", orderIndex = 5),
        Channel(id = "cctv5plus", name = "CCTV-5+ 体育赛事", channelNumber = 6, categoryId = "cctv", orderIndex = 6),
        Channel(id = "cctv6", name = "CCTV-6 电影", channelNumber = 7, categoryId = "cctv", orderIndex = 7),
        Channel(id = "cctv7", name = "CCTV-7 国防军事", channelNumber = 8, categoryId = "cctv", orderIndex = 8),
        Channel(id = "cctv8", name = "CCTV-8 电视剧", channelNumber = 9, categoryId = "cctv", orderIndex = 9),
        Channel(id = "cctv9", name = "CCTV-9 纪录", channelNumber = 10, categoryId = "cctv", orderIndex = 10),
        Channel(id = "cctv10", name = "CCTV-10 科教", channelNumber = 11, categoryId = "cctv", orderIndex = 11),
        Channel(id = "cctv11", name = "CCTV-11 戏曲", channelNumber = 12, categoryId = "cctv", orderIndex = 12),
        Channel(id = "cctv12", name = "CCTV-12 社会与法", channelNumber = 13, categoryId = "cctv", orderIndex = 13),
        Channel(id = "cctv13", name = "CCTV-13 新闻", channelNumber = 14, categoryId = "cctv", orderIndex = 14),
        Channel(id = "cctv14", name = "CCTV-14 少儿", channelNumber = 15, categoryId = "cctv", orderIndex = 15),
        Channel(id = "cctv15", name = "CCTV-15 音乐", channelNumber = 16, categoryId = "cctv", orderIndex = 16),
        Channel(id = "cctv16", name = "CCTV-16 奥林匹克", channelNumber = 17, categoryId = "cctv", orderIndex = 17),
        Channel(id = "cctv17", name = "CCTV-17 农业农村", channelNumber = 18, categoryId = "cctv", orderIndex = 18)
    )

    val satelliteChannels = listOf(
        Channel(id = "sat_beijing", name = "北京卫视", channelNumber = 101, categoryId = "satellite", orderIndex = 101),
        Channel(id = "sat_jiangsu", name = "江苏卫视", channelNumber = 102, categoryId = "satellite", orderIndex = 102),
        Channel(id = "sat_zhejiang", name = "浙江卫视", channelNumber = 103, categoryId = "satellite", orderIndex = 103),
        Channel(id = "sat_hunan", name = "湖南卫视", channelNumber = 104, categoryId = "satellite", orderIndex = 104),
        Channel(id = "sat_dragon", name = "东方卫视", channelNumber = 105, categoryId = "satellite", orderIndex = 105),
        Channel(id = "sat_guangdong", name = "广东卫视", channelNumber = 106, categoryId = "satellite", orderIndex = 106),
        Channel(id = "sat_shandong", name = "山东卫视", channelNumber = 107, categoryId = "satellite", orderIndex = 107),
        Channel(id = "sat_anhui", name = "安徽卫视", channelNumber = 108, categoryId = "satellite", orderIndex = 108),
        Channel(id = "sat_shenzhen", name = "深圳卫视", channelNumber = 109, categoryId = "satellite", orderIndex = 109)
    )

    val allDefaultChannels = cctvChannels + satelliteChannels
}
