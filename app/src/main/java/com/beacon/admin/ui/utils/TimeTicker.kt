package com.beacon.admin.ui.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun tickerFlow(periodMillis: Long): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMillis)
    }
}
