package com.livestream.data.epg.mapper

import com.livestream.core.database.entity.EpgProgramEntity
import com.livestream.core.model.EpgProgram

fun EpgProgramEntity.toDomain(): EpgProgram {
    return EpgProgram(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp
    )
}

fun EpgProgram.toEntity(): EpgProgramEntity {
    return EpgProgramEntity(
        id = id,
        channelId = channelId,
        title = title,
        description = description,
        startTimestamp = startTimestamp,
        endTimestamp = endTimestamp,
        updatedAt = System.currentTimeMillis()
    )
}
