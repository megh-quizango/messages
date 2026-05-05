package com.text.messages.sms.messanger.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * Shared dispatchers to limit thread creation on low-end devices.
 * ARM Cortex-A53 has 4-8 cores; creating too many threads causes context switching overhead.
 */
object AppDispatchers {
    /** For database operations and I/O - limited to 2 threads */
    val io: CoroutineDispatcher = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    /** For SDK initialization and one-off background tasks */
    val sdkInit: CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}
