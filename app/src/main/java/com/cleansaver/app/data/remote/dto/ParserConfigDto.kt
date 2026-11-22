package com.cleansaver.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ParserConfigDto(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("min_app_version_code") val minAppVersionCode: Int,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean,
    @SerialName("maintenance_message") val maintenanceMessage: String = "",
    @SerialName("config_ttl_hours") val configTtlHours: Int = 24,
    @SerialName("user_agents") val userAgents: List<String>,
    @SerialName("url_patterns") val urlPatterns: UrlPatternsDto,
    @SerialName("strategies") val strategies: List<StrategyDto>
)

@Serializable
data class UrlPatternsDto(
    @SerialName("reel_regex") val reelRegex: String,
    @SerialName("post_regex") val postRegex: String,
    @SerialName("stories_regex") val storiesRegex: String
)

@Serializable
data class StrategyDto(
    @SerialName("id") val id: String,
    @SerialName("type") val type: String,
    @SerialName("enabled") val enabled: Boolean,
    @SerialName("endpoint_template") val endpointTemplate: String,
    @SerialName("video_regex") val videoRegex: String? = null,
    @SerialName("image_regex") val imageRegex: String? = null,
    @SerialName("json_script_selector") val jsonScriptSelector: String? = null,
    @SerialName("timeout_ms") val timeoutMs: Long = 8000L
)

