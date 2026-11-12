package com.example.core

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "AiNotificationDigestRepo"

enum class AiDigestScope(val id: String, val label: String) {
    LAST_SESSION("last_session", "Last Session"),
    UNREAD_ONLY("unread_only", "Unread Alerts"),
    ALL_RECENT("all_recent", "All Missed")
}

/**
 * User-selectable Gemini model presets for strictly BYOK (Bring Your Own Key) requests.
 */
enum class GeminiModelPreset(
    val modelId: String,
    val displayName: String,
    val badgeLabel: String,
    val description: String
) {
    GEMINI_2_5_FLASH(
        modelId = "gemini-2.5-flash",
        displayName = "Gemini 2.5 Flash",
        badgeLabel = "2.5 FLASH",
        description = "Fast, low-latency executive digests (Recommended)"
    ),
    GEMINI_2_5_PRO(
        modelId = "gemini-2.5-pro",
        displayName = "Gemini 2.5 Pro",
        badgeLabel = "2.5 PRO",
        description = "Deep reasoning & complex multi-thread synthesis"
    ),
    GEMINI_2_5_FLASH_LITE(
        modelId = "gemini-2.5-flash-lite",
        displayName = "Gemini 2.5 Flash-Lite",
        badgeLabel = "2.5 LITE",
        description = "Ultra-fast, lightweight alert summaries"
    );

    companion object {
        fun fromModelIdOrName(raw: String?): GeminiModelPreset {
            if (raw.isNullOrBlank()) return GEMINI_2_5_FLASH
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.modelId.equals(raw, ignoreCase = true)
            } ?: GEMINI_2_5_FLASH
        }
    }
}

data class SenderDigestGroup(
    val senderOrApp: String = "",
    val categoryLabel: String = "General",
    val count: Int = 1,
    val summary: String = ""
)

internal data class GeminiDigestJsonPayload(
    val headline: String? = null,
    val urgentActionItems: List<String>? = null,
    val senderGroups: List<SenderDigestGroup>? = null,
    val focusTip: String? = null
)

internal data class GeminiRestPart(
    val text: String? = null
)

internal data class GeminiRestContent(
    val parts: List<GeminiRestPart>? = null
)

internal data class GeminiRestCandidate(
    val content: GeminiRestContent? = null
)

internal data class GeminiRestErrorDetail(
    val code: Int? = null,
    val message: String? = null,
    val status: String? = null
)

internal data class GeminiRestResponse(
    val candidates: List<GeminiRestCandidate>? = null,
    val error: GeminiRestErrorDetail? = null
)

data class AiNotificationDigest(
    val id: Long = 0L,
    val generatedAtMillis: Long = System.currentTimeMillis(),
    val scopeLabel: String,
    val focusProfileDisplayName: String,
    val totalNotificationsAnalyzed: Int,
    val totalCallsAnalyzed: Int,
    val headline: String,
    val urgentActionItems: List<String>,
    val senderGroups: List<SenderDigestGroup>,
    val focusTip: String
) {
    val formattedGeneratedTime: String
        get() = SimpleDateFormat("MMM d • HH:mm", Locale.getDefault()).format(Date(generatedAtMillis))
}

data class SessionEndDigestTrigger(
    val sessionStartTimeMillis: Long,
    val endedProfile: FocusProfile
)

/**
 * Repository managing the Room-backed Missed Notification & Call Log and strictly BYOK
 * (Bring Your Own Key) Google Gemini REST API Executive Digest generation.
 *
 * Every AI request authenticates directly against `generativelanguage.googleapis.com`
 * using the end-user's personal Gemini API key—never a developer key.
 */
class AiNotificationDigestRepository(
    private val dao: MissedNotificationDao,
    private val httpClient: OkHttpClient = defaultOkHttpClient
) {
    val allMissedNotificationsFlow: Flow<List<MissedNotificationEntity>> =
        dao.getAllMissedNotificationsFlow()

    val unreadMissedNotificationsFlow: Flow<List<MissedNotificationEntity>> =
        dao.getUnreadMissedNotificationsFlow()

    val latestDigestFlow: Flow<AiNotificationDigest?> =
        dao.getLatestDigestFlow().map { entity -> entity?.let { decodeEntityToDomain(it) } }

    private val _sessionEndDigestTriggers = MutableSharedFlow<SessionEndDigestTrigger>(extraBufferCapacity = 4)
    val sessionEndDigestTriggers: SharedFlow<SessionEndDigestTrigger> = _sessionEndDigestTriggers.asSharedFlow()

    fun notifyFocusSessionEndedForAutoDigest(
        sessionStartTimeMillis: Long,
        endedProfile: FocusProfile
    ) {
        if (endedProfile == FocusProfile.OFF) return
        _sessionEndDigestTriggers.tryEmit(
            SessionEndDigestTrigger(
                sessionStartTimeMillis = sessionStartTimeMillis,
                endedProfile = endedProfile
            )
        )
    }

    suspend fun logMissedNotificationOrCall(
        focusProfile: FocusProfile,
        sessionStartTimeMillis: Long?,
        appName: String,
        packageName: String,
        senderOrTitle: String,
        messagePreview: String,
        category: NotificationCategory,
        priorityLevel: NotificationPriorityLevel,
        appliedAction: GlobalFocusRuleAction,
        isCallBounce: Boolean = false,
        timestampMillis: Long = System.currentTimeMillis()
    ): Long {
        val resolvedProfile = if (focusProfile == FocusProfile.OFF) {
            FocusProfile.DO_NOT_DISTURB
        } else {
            focusProfile
        }
        val entity = MissedNotificationEntity(
            timestampMillis = timestampMillis,
            sessionStartTimeMillis = sessionStartTimeMillis ?: 0L,
            focusProfileId = resolvedProfile.id,
            focusProfileDisplayName = resolvedProfile.displayName,
            appName = appName.ifBlank { packageName },
            packageName = packageName.ifBlank { "com.sentinal.shield" },
            senderOrTitle = senderOrTitle.ifBlank { appName },
            messagePreview = messagePreview.ifBlank { "Silenced during ${resolvedProfile.displayName} Focus" },
            categoryName = category.name,
            priorityLevelName = priorityLevel.name,
            appliedActionName = appliedAction.name,
            isCallBounce = isCallBounce,
            isReviewed = false
        )
        return dao.insertMissedNotification(entity)
    }

    suspend fun markReviewed(id: Long, reviewed: Boolean = true) {
        dao.setReviewedStatus(id, reviewed)
    }

    suspend fun markAllReviewed() {
        dao.markAllAsReviewed()
    }

    suspend fun deleteMissedNotification(id: Long) {
        dao.deleteMissedNotificationById(id)
    }

    suspend fun clearAllMissedNotifications() {
        dao.clearAllMissedNotifications()
    }

    suspend fun getMissedNotificationsSince(sinceMillis: Long): List<MissedNotificationEntity> {
        return dao.getMissedNotificationsSince(sinceMillis)
    }

    suspend fun getAllMissedNotificationsSnapshot(): List<MissedNotificationEntity> {
        return dao.getAllMissedNotificationsSnapshot()
    }

    /**
     * Verifies the user's personal BYOK Gemini API key against the selected Gemini model preset.
     */
    suspend fun verifyUserGeminiApiKey(
        userApiKey: String,
        modelPreset: GeminiModelPreset = GeminiModelPreset.GEMINI_2_5_FLASH
    ): Result<String> {
        val trimmedKey = userApiKey.trim()
        if (trimmedKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException("Please enter your personal Gemini API key first.")
            )
        }

        val requestPayload = mapOf(
            "contents" to listOf(
                mapOf(
                    "parts" to listOf(
                        mapOf("text" to "Reply with READY")
                    )
                )
            ),
            "generationConfig" to mapOf(
                "maxOutputTokens" to 8
            )
        )
        val requestJson = anyMapAdapter.toJson(requestPayload)

        return try {
            withTimeout(15_000L) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$GEMINI_API_BASE_URL/${modelPreset.modelId}:generateContent")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-goog-api-key", trimmedKey)
                        .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val apiErr = runCatching {
                                restResponseAdapter.fromJson(bodyStr)?.error?.message
                            }.getOrNull()
                            val detail = apiErr?.takeIf { it.isNotBlank() }
                                ?: "HTTP ${response.code}: Invalid or unauthorized Gemini API key."
                            return@withContext Result.failure(IllegalStateException(detail))
                        }
                        Result.success("Verified • ${modelPreset.displayName} connected with your BYOK key")
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Key verification timed out after 15s. Check your network connection.", e))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.localizedMessage ?: "Unable to verify Gemini API key.", e))
        }
    }

    /**
     * Generates an AI Executive Notification Digest strictly using the user's personal BYOK Gemini API key
     * and selected [GeminiModelPreset] with structured JSON schema output and Room persistence.
     */
    suspend fun generateExecutiveDigest(
        items: List<MissedNotificationEntity>,
        scopeLabel: String,
        focusProfileDisplayName: String,
        userApiKey: String,
        modelPreset: GeminiModelPreset = GeminiModelPreset.GEMINI_2_5_FLASH
    ): Result<AiNotificationDigest> {
        val cleanKey = userApiKey.trim()
        if (cleanKey.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "BYOK Gemini API Key Required: Enter your personal Gemini API key in the BYOK Key Vault to generate AI summaries."
                )
            )
        }

        if (items.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("No missed notifications or bounced calls available to summarize for '$scopeLabel'.")
            )
        }

        val targetItems = items.take(35)
        val totalCalls = targetItems.count { it.isCallBounce }
        val totalAlerts = targetItems.size - totalCalls
        val prompt = buildExecutiveDigestPrompt(
            items = targetItems,
            scopeLabel = scopeLabel,
            focusProfileDisplayName = focusProfileDisplayName
        )

        val requestJson = buildStructuredGenerateContentRequestJson(prompt)

        return try {
            val rawJson = withTimeout(60_000L) {
                withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url("$GEMINI_API_BASE_URL/${modelPreset.modelId}:generateContent")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("x-goog-api-key", cleanKey)
                        .post(requestJson.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val apiErrorMsg = runCatching {
                                restResponseAdapter.fromJson(bodyString)?.error?.message
                            }.getOrNull()
                            throw IllegalStateException(
                                apiErrorMsg?.takeIf { it.isNotBlank() }
                                    ?: "Gemini API Error (HTTP ${response.code}): Please check your BYOK Gemini API key."
                            )
                        }
                        extractCandidateTextFromRestResponse(bodyString)
                    }
                }
            }

            if (rawJson.isBlank()) {
                return Result.failure(IllegalStateException("Gemini returned an empty response."))
            }

            val parsedDigest = parseDigestJson(
                jsonString = rawJson,
                scopeLabel = scopeLabel,
                focusProfileDisplayName = focusProfileDisplayName,
                totalNotificationsAnalyzed = totalAlerts,
                totalCallsAnalyzed = totalCalls
            )

            val savedEntity = encodeDomainToEntity(parsedDigest)
            val insertedId = dao.insertDigest(savedEntity)
            Result.success(parsedDigest.copy(id = insertedId))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "BYOK Gemini Digest generation timed out after 60s", e)
            Result.failure(
                IllegalStateException("AI digest generation timed out after 60 seconds. Please try again.", e)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "BYOK Gemini Notification Digest generation failed", e)
            Result.failure(e)
        }
    }

    companion object {
        private const val GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build()
        }

        private val moshi: Moshi by lazy {
            Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }

        private val payloadAdapter by lazy {
            moshi.adapter(GeminiDigestJsonPayload::class.java)
        }

        private val restResponseAdapter by lazy {
            moshi.adapter(GeminiRestResponse::class.java)
        }

        private val anyMapAdapter by lazy {
            val type = Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
            moshi.adapter<Map<String, Any>>(type)
        }

        private val stringListAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, String::class.java)
            moshi.adapter<List<String>>(type)
        }

        private val senderGroupListAdapter by lazy {
            val type = Types.newParameterizedType(List::class.java, SenderDigestGroup::class.java)
            moshi.adapter<List<SenderDigestGroup>>(type)
        }

        /**
         * Masks a user's BYOK Gemini API key for safe visual status display.
         */
        fun maskUserApiKey(rawKey: String): String {
            val trimmed = rawKey.trim()
            if (trimmed.isEmpty()) return "Not Configured"
            if (trimmed.length <= 8) return "••••••••"
            return "${trimmed.take(4)}••••••••${trimmed.takeLast(4)}"
        }

        /**
         * Builds the Gemini REST `generateContent` JSON request body with structured `responseSchema`.
         */
        fun buildStructuredGenerateContentRequestJson(prompt: String): String {
            val schemaMap = mapOf(
                "type" to "OBJECT",
                "properties" to mapOf(
                    "headline" to mapOf(
                        "type" to "STRING",
                        "description" to "Concise 1-2 sentence executive briefing of what the user missed while their Sentinal Focus shield was active"
                    ),
                    "urgentActionItems" to mapOf(
                        "type" to "ARRAY",
                        "description" to "2 to 4 prioritized action items the user should address first",
                        "items" to mapOf(
                            "type" to "STRING"
                        )
                    ),
                    "senderGroups" to mapOf(
                        "type" to "ARRAY",
                        "description" to "Grouped summaries consolidated by sender or source application",
                        "items" to mapOf(
                            "type" to "OBJECT",
                            "properties" to mapOf(
                                "senderOrApp" to mapOf("type" to "STRING"),
                                "categoryLabel" to mapOf("type" to "STRING"),
                                "count" to mapOf("type" to "INTEGER"),
                                "summary" to mapOf("type" to "STRING")
                            ),
                            "required" to listOf("senderOrApp", "categoryLabel", "count", "summary")
                        )
                    ),
                    "focusTip" to mapOf(
                        "type" to "STRING",
                        "description" to "Brief, practical focus hygiene or rule recommendation based on the observed interruption pattern"
                    )
                ),
                "required" to listOf("headline", "urgentActionItems", "senderGroups", "focusTip")
            )

            val requestBodyMap = mapOf(
                "contents" to listOf(
                    mapOf(
                        "parts" to listOf(
                            mapOf("text" to prompt)
                        )
                    )
                ),
                "generationConfig" to mapOf(
                    "responseMimeType" to "application/json",
                    "responseSchema" to schemaMap
                )
            )
            return anyMapAdapter.toJson(requestBodyMap)
        }

        /**
         * Extracts the JSON text payload from the Gemini REST API `candidates[0].content.parts[0].text` response.
         */
        fun extractCandidateTextFromRestResponse(rawRestResponseJson: String): String {
            val parsed = restResponseAdapter.fromJson(rawRestResponseJson)
                ?: throw IllegalArgumentException("Malformed Gemini REST response")
            if (parsed.error != null) {
                throw IllegalStateException(parsed.error.message ?: "Gemini API returned an error")
            }
            return parsed.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                .orEmpty()
        }

        fun buildExecutiveDigestPrompt(
            items: List<MissedNotificationEntity>,
            scopeLabel: String,
            focusProfileDisplayName: String
        ): String {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val lines = items.mapIndexed { idx, item ->
                val timeStr = timeFormat.format(Date(item.timestampMillis))
                val kind = if (item.isCallBounce) "BOUNCED_CALL" else "SILENCED_ALERT"
                "${idx + 1}. [$timeStr] [$kind] [Mode=${item.focusProfileDisplayName}] " +
                    "[Priority=${item.priorityLevelName}] [Category=${item.categoryName}] " +
                    "App=\"${item.appName}\" | Sender/Title=\"${item.senderOrTitle}\" | " +
                    "Content=\"${item.messagePreview}\""
            }.joinToString("\n")

            return """
                You are the executive focus intelligence engine inside the Sentinal Android Focus & Call Shield app.
                Summarize the following ${items.size} missed notifications and bounced calls captured under scope "$scopeLabel" (primary mode: $focusProfileDisplayName).
                
                Requirements:
                1. Provide a crisp 1-2 sentence executive "headline" summarizing the most important activity missed.
                2. Extract 2-4 concrete "urgentActionItems" prioritized by urgency (bounced calls, P1/P2 direct messages, work/calendar deadlines).
                3. Consolidate related notifications by sender or app into "senderGroups" with accurate counts and a 1-sentence summary per group.
                4. Provide one concise "focusTip" tailored to the observed interruption pattern.
                
                Intercepted Notification & Call Log:
                $lines
            """.trimIndent()
        }

        fun parseDigestJson(
            jsonString: String,
            scopeLabel: String,
            focusProfileDisplayName: String,
            totalNotificationsAnalyzed: Int,
            totalCallsAnalyzed: Int,
            generatedAtMillis: Long = System.currentTimeMillis()
        ): AiNotificationDigest {
            val payload = payloadAdapter.fromJson(jsonString)
                ?: throw IllegalArgumentException("Unable to parse structured AI Digest JSON")

            val headline = payload.headline?.takeIf { it.isNotBlank() }
                ?: "Missed notifications summarized."

            val urgentItems = payload.urgentActionItems.orEmpty()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val senderGroups = payload.senderGroups.orEmpty()
                .filter { it.senderOrApp.isNotBlank() }
                .map { group ->
                    group.copy(
                        senderOrApp = group.senderOrApp.trim(),
                        categoryLabel = group.categoryLabel.ifBlank { "General" },
                        count = group.count.coerceAtLeast(1),
                        summary = group.summary.trim()
                    )
                }

            val focusTip = payload.focusTip?.takeIf { it.isNotBlank() }
                ?: "Keep repeat-caller breakthrough active for urgent contacts while silencing batch notifications."

            return AiNotificationDigest(
                generatedAtMillis = generatedAtMillis,
                scopeLabel = scopeLabel,
                focusProfileDisplayName = focusProfileDisplayName,
                totalNotificationsAnalyzed = totalNotificationsAnalyzed,
                totalCallsAnalyzed = totalCallsAnalyzed,
                headline = headline,
                urgentActionItems = urgentItems,
                senderGroups = senderGroups,
                focusTip = focusTip
            )
        }

        fun encodeDomainToEntity(digest: AiNotificationDigest): AiNotificationDigestEntity {
            return AiNotificationDigestEntity(
                id = digest.id,
                generatedAtMillis = digest.generatedAtMillis,
                scopeLabel = digest.scopeLabel,
                focusProfileDisplayName = digest.focusProfileDisplayName,
                totalNotificationsAnalyzed = digest.totalNotificationsAnalyzed,
                totalCallsAnalyzed = digest.totalCallsAnalyzed,
                headline = digest.headline,
                urgentActionItemsJson = stringListAdapter.toJson(digest.urgentActionItems),
                senderGroupsJson = senderGroupListAdapter.toJson(digest.senderGroups),
                focusTip = digest.focusTip
            )
        }

        fun decodeEntityToDomain(entity: AiNotificationDigestEntity): AiNotificationDigest {
            val urgentItems = runCatching {
                stringListAdapter.fromJson(entity.urgentActionItemsJson).orEmpty()
            }.getOrDefault(emptyList())

            val senderGroups = runCatching {
                senderGroupListAdapter.fromJson(entity.senderGroupsJson).orEmpty()
            }.getOrDefault(emptyList())

            return AiNotificationDigest(
                id = entity.id,
                generatedAtMillis = entity.generatedAtMillis,
                scopeLabel = entity.scopeLabel,
                focusProfileDisplayName = entity.focusProfileDisplayName,
                totalNotificationsAnalyzed = entity.totalNotificationsAnalyzed,
                totalCallsAnalyzed = entity.totalCallsAnalyzed,
                headline = entity.headline,
                urgentActionItems = urgentItems,
                senderGroups = senderGroups,
                focusTip = entity.focusTip
            )
        }
    }
}
