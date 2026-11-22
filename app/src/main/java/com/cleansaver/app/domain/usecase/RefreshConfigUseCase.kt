package com.cleansaver.app.domain.usecase

import com.cleansaver.app.domain.repository.RemoteConfigRepository

class RefreshConfigUseCase(
    private val remoteConfigRepository: RemoteConfigRepository
) {
    suspend operator fun invoke(force: Boolean = true): Result<Unit> {
        return remoteConfigRepository.refreshConfig(force)
    }
}

