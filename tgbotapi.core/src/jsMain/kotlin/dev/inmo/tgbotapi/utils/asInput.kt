package dev.inmo.tgbotapi.utils

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.Input
import io.ktor.utils.io.toByteArray

actual suspend fun ByteReadChannel.asInput(): Input = ByteReadPacket(toByteArray())
