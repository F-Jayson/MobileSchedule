package com.example.mobileschedule.data.model

/** The ViewModel adds Loading; an empty Ok value is never a storage failure. */
sealed interface RepoResult<out T> {
    data class Ok<T>(val value: T) : RepoResult<T>
    data class Err(val error: DataError) : RepoResult<Nothing>
}

enum class DataErrorCode {
    CONFIG_REQUIRED, CONFIG_INVALID, CONFIG_CONFLICT,
    SEMESTER_NOT_FOUND, WEEK_OUT_OF_RANGE,
    STORAGE_READ_FAILED, STORAGE_WRITE_FAILED,
}

data class DataError(
    val code: DataErrorCode,
    val issues: List<RuleIssue> = emptyList(),
    /** Distinct existing course arrangements outside the proposed week/section limits. */
    val affectedCount: Int? = null,
)
