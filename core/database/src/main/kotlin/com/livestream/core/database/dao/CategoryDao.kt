package com.livestream.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.livestream.core.database.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY order_index ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :categoryId LIMIT 1")
    suspend fun getCategoryById(categoryId: String): CategoryEntity?

    @Upsert
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategoriesIgnore(categories: List<CategoryEntity>)

    @Upsert
    suspend fun insertCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun deleteCategoryById(categoryId: String)

    @Query("DELETE FROM categories WHERE id LIKE 'custom_%'")
    suspend fun deleteAllCustomCategories(): Int

    @Query("DELETE FROM categories WHERE id LIKE 'custom_%' AND id NOT IN (SELECT DISTINCT category_id FROM channels)")
    suspend fun deleteOrphanedCustomCategories(): Int
}
