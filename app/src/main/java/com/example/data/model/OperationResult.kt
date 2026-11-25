package com.example.data.model

/**
 * Typed domain errors returned by repository and widget operations.
 */
sealed interface AppError {
    val message: String

    data class PermissionDenied(
        val permission: String,
        override val message: String
    ) : AppError

    data class CalendarQueryFailed(
        override val message: String,
        val cause: Throwable? = null
    ) : AppError

    data class EventWriteFailed(
        val operation: String,
        override val message: String,
        val cause: Throwable? = null
    ) : AppError

    data class DatabaseFailed(
        override val message: String,
        val cause: Throwable? = null
    ) : AppError

    data class Unexpected(
        override val message: String,
        val cause: Throwable? = null
    ) : AppError
}

/**
 * Explicit outcome wrapper for repository mutations and synchronization calls.
 */
sealed class OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure(val error: AppError) : OperationResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): AppError? = (this as? Failure)?.error

    inline fun onSuccess(block: (T) -> Unit): OperationResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onFailure(block: (AppError) -> Unit): OperationResult<T> {
        if (this is Failure) block(error)
        return this
    }
}
