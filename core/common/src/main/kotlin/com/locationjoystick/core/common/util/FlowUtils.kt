package com.locationjoystick.core.common.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.onEach

fun <T> Flow<T>.throttleLatest(periodMs: Long): Flow<T> = conflate().onEach { delay(periodMs) }
