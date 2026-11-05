package com.livestream.data.channel.mapper

import com.livestream.core.database.entity.CategoryEntity
import com.livestream.core.model.ChannelCategory
import com.livestream.core.model.ChannelCategoryGroup

fun CategoryEntity.toDomain(): ChannelCategory {
    val groupEnum = when (group) {
        "CCTV" -> ChannelCategoryGroup.CCTV
        "SATELLITE" -> ChannelCategoryGroup.SATELLITE
        "LOCAL" -> ChannelCategoryGroup.LOCAL
        else -> ChannelCategoryGroup.CUSTOM
    }
    return ChannelCategory(
        id = id,
        name = name,
        group = groupEnum,
        orderIndex = orderIndex
    )
}

fun ChannelCategory.toEntity(): CategoryEntity {
    return CategoryEntity(
        id = id,
        name = name,
        group = group.name,
        orderIndex = orderIndex
    )
}
