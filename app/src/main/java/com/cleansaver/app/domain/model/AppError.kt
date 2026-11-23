package com.cleansaver.app.domain.model

sealed interface AppError {
    data object InvalidUrl : AppError
    data object StoriesNotSupported : AppError
    data object PrivateAccountNotSupported : AppError
    data object PostNotFound : AppError
    data object NetworkRateLimited : AppError
    data object ParserOutdated : AppError
    data object NoInternet : AppError
    data object WifiOnlyBlocked : AppError
    data class StorageError(val reason: String) : AppError
    data class Unknown(val message: String) : AppError
}

class CustomThrowable(val appError: AppError) : Throwable(appError.toString())
