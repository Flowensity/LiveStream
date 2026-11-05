package com.livestream.data.channel.mapper

import com.livestream.core.database.entity.ChannelEntity
import com.livestream.core.model.Channel

fun ChannelEntity.toDomain(): Channel {
    return Channel(
        id = id,
        name = name,
        channelNumber = channelNumber,
        categoryId = categoryId,
        logoUrl = logoUrl,
        isFavorite = isFavorite,
        isHidden = isHidden,
        orderIndex = orderIndex,
        epgSourceId = epgSourceId
    )
}

fun Channel.toEntity(): ChannelEntity {
    return ChannelEntity(
        id = id,
        name = name,
        channelNumber = channelNumber,
        categoryId = categoryId,
        logoUrl = logoUrl,
        isFavorite = isFavorite,
        isHidden = isHidden,
        orderIndex = orderIndex,
        epgSourceId = epgSourceId
    )
}
