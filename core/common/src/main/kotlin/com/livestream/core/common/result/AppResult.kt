package com.livestream.core.common.result

/**
 * 通用业务结果封装
 */
sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val throwable: Throwable, val message: String? = throwable.message) : AppResult<Nothing>
    data object Loading : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> {
    return when (this) {
        is AppResult.Success -> AppResult.Success(transform(data))
        is AppResult.Error -> AppResult.Error(throwable, message)
        is AppResult.Loading -> AppResult.Loading
    }
}
