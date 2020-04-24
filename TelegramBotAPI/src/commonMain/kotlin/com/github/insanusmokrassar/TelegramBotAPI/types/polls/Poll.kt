package com.github.insanusmokrassar.TelegramBotAPI.types.polls

import com.github.insanusmokrassar.TelegramBotAPI.CommonAbstracts.CaptionedInput
import com.github.insanusmokrassar.TelegramBotAPI.CommonAbstracts.TextPart
import com.github.insanusmokrassar.TelegramBotAPI.types.*
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageEntity.*
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageEntity.RawMessageEntity
import com.github.insanusmokrassar.TelegramBotAPI.types.MessageEntity.asTextParts
import com.github.insanusmokrassar.TelegramBotAPI.utils.nonstrictJsonFormat
import com.soywiz.klock.DateTime
import com.soywiz.klock.TimeSpan
import kotlinx.serialization.*
import kotlinx.serialization.json.*

sealed class ScheduledCloseInfo {
    abstract val closeDateTime: DateTime
}

data class ExactScheduledCloseInfo(
    override val closeDateTime: DateTime
) : ScheduledCloseInfo()

data class ApproximateScheduledCloseInfo(
    val openDuration: TimeSpan,
    @Suppress("MemberVisibilityCanBePrivate")
    val startPoint: DateTime = DateTime.now()
) : ScheduledCloseInfo() {
    override val closeDateTime: DateTime = startPoint + openDuration
}

val LongSeconds.asApproximateScheduledCloseInfo
    get() = ApproximateScheduledCloseInfo(
        TimeSpan(this * 1000.0)
    )
val LongSeconds.asExactScheduledCloseInfo
    get() = ExactScheduledCloseInfo(
        DateTime(unixMillis = this * 1000.0)
    )

@Serializable(PollSerializer::class)
sealed class Poll {
    abstract val id: PollIdentifier
    abstract val question: String
    abstract val options: List<PollOption>
    abstract val votesCount: Int
    abstract val isClosed: Boolean
    abstract val isAnonymous: Boolean
    abstract val scheduledCloseInfo: ScheduledCloseInfo?
}

@Serializable(PollSerializer::class)
sealed class MultipleAnswersPoll : Poll()

@Serializable
private class RawPoll(
    @SerialName(idField)
    val id: PollIdentifier,
    @SerialName(questionField)
    val question: String,
    @SerialName(optionsField)
    val options: List<PollOption>,
    @SerialName(totalVoterCountField)
    val votesCount: Int,
    @SerialName(isClosedField)
    val isClosed: Boolean = false,
    @SerialName(isAnonymousField)
    val isAnonymous: Boolean = false,
    @SerialName(typeField)
    val type: String,
    @SerialName(allowsMultipleAnswersField)
    val allowMultipleAnswers: Boolean = false,
    @SerialName(correctOptionIdField)
    val correctOptionId: Int? = null,
    @SerialName(explanationField)
    val caption: String? = null,
    @SerialName(explanationEntitiesField)
    val captionEntities: List<RawMessageEntity> = emptyList(),
    @SerialName(openPeriodField)
    val openPeriod: LongSeconds? = null,
    @SerialName(closeDateField)
    val closeDate: LongSeconds? = null
) {
    @Transient
    val scheduledCloseInfo: ScheduledCloseInfo?
        = closeDate ?.asExactScheduledCloseInfo ?: openPeriod ?.asApproximateScheduledCloseInfo
}

@Serializable
data class UnknownPollType internal constructor(
    @SerialName(idField)
    override val id: PollIdentifier,
    @SerialName(questionField)
    override val question: String,
    @SerialName(optionsField)
    override val options: List<PollOption>,
    @SerialName(totalVoterCountField)
    override val votesCount: Int,
    @SerialName(isClosedField)
    override val isClosed: Boolean = false,
    @SerialName(isAnonymousField)
    override val isAnonymous: Boolean = false,
    @Serializable
    val raw: JsonObject
) : Poll() {
    @Transient
    override val scheduledCloseInfo: ScheduledCloseInfo? = raw.getPrimitiveOrNull(
        closeDateField
    ) ?.longOrNull ?.asExactScheduledCloseInfo ?: raw.getPrimitiveOrNull(
        openPeriodField
    ) ?.longOrNull ?.asApproximateScheduledCloseInfo
}

@Serializable(PollSerializer::class)
data class RegularPoll(
    override val id: PollIdentifier,
    override val question: String,
    override val options: List<PollOption>,
    override val votesCount: Int,
    override val isClosed: Boolean = false,
    override val isAnonymous: Boolean = false,
    val allowMultipleAnswers: Boolean = false,
    override val scheduledCloseInfo: ScheduledCloseInfo? = null
) : MultipleAnswersPoll()

@Serializable(PollSerializer::class)
data class QuizPoll(
    override val id: PollIdentifier,
    override val question: String,
    override val options: List<PollOption>,
    override val votesCount: Int,
    /**
     * Nullable due to documentation (https://core.telegram.org/bots/api#poll)
     */
    val correctOptionId: Int? = null,
    override val caption: String? = null,
    override val captionEntities: List<TextPart> = emptyList(),
    override val isClosed: Boolean = false,
    override val isAnonymous: Boolean = false,
    override val scheduledCloseInfo: ScheduledCloseInfo? = null
) : Poll(), CaptionedInput

@Serializer(Poll::class)
internal object PollSerializer : KSerializer<Poll> {
    override val descriptor: SerialDescriptor
        get() = RawPoll.serializer().descriptor

    override fun deserialize(decoder: Decoder): Poll {
        val asJson = JsonObjectSerializer.deserialize(decoder)
        val rawPoll = nonstrictJsonFormat.fromJson(RawPoll.serializer(), asJson)

        return when (rawPoll.type) {
            quizPollType -> QuizPoll(
                rawPoll.id,
                rawPoll.question,
                rawPoll.options,
                rawPoll.votesCount,
                rawPoll.correctOptionId,
                rawPoll.caption,
                rawPoll.caption?.let { rawPoll.captionEntities.asTextParts(it) } ?: emptyList(),
                rawPoll.isClosed,
                rawPoll.isAnonymous,
                rawPoll.scheduledCloseInfo
            )
            regularPollType -> RegularPoll(
                rawPoll.id,
                rawPoll.question,
                rawPoll.options,
                rawPoll.votesCount,
                rawPoll.isClosed,
                rawPoll.isAnonymous,
                rawPoll.allowMultipleAnswers,
                rawPoll.scheduledCloseInfo
            )
            else -> UnknownPollType(
                rawPoll.id,
                rawPoll.question,
                rawPoll.options,
                rawPoll.votesCount,
                rawPoll.isClosed,
                rawPoll.isAnonymous,
                asJson
            )
        }
    }

    override fun serialize(encoder: Encoder, value: Poll) {
        val closeInfo = value.scheduledCloseInfo
        val rawPoll = when (value) {
            is RegularPoll -> RawPoll(
                value.id,
                value.question,
                value.options,
                value.votesCount,
                value.isClosed,
                value.isAnonymous,
                regularPollType,
                value.allowMultipleAnswers,
                openPeriod = (closeInfo as? ApproximateScheduledCloseInfo) ?.openDuration ?.seconds ?.toLong(),
                closeDate = (closeInfo as? ExactScheduledCloseInfo) ?.closeDateTime ?.unixMillisLong ?.div(1000L)
            )
            is QuizPoll -> RawPoll(
                value.id,
                value.question,
                value.options,
                value.votesCount,
                value.isClosed,
                value.isAnonymous,
                regularPollType,
                correctOptionId = value.correctOptionId,
                caption = value.caption,
                captionEntities = value.captionEntities.asRawMessageEntities(),
                openPeriod = (closeInfo as? ApproximateScheduledCloseInfo) ?.openDuration ?.seconds ?.toLong(),
                closeDate = (closeInfo as? ExactScheduledCloseInfo) ?.closeDateTime ?.unixMillisLong ?.div(1000L)
            )
            is UnknownPollType -> {
                JsonObjectSerializer.serialize(encoder, value.raw)
                return
            }
        }
        RawPoll.serializer().serialize(encoder, rawPoll)
    }
}
