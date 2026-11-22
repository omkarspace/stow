package com.cleansaver.app.domain.repository

import com.cleansaver.app.data.remote.dto.ParserConfigDto

interface RemoteConfigRepository {
    suspend fun getActiveConfig(forceRefresh: Boolean = false): ParserConfigDto
    suspend fun refreshConfig(force: Boolean = true): Result<Unit>
}

