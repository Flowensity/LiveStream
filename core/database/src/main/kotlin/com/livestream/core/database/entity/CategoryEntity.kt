package com.livestream.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "category_group")
    val group: String, // "CCTV", "SATELLITE", "LOCAL", "CUSTOM"
    @ColumnInfo(name = "order_index")
    val orderIndex: Int = 0
)
