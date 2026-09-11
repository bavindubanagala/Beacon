package com.beacon.admin.ui.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Safely collects a flow in a lifecycle-aware manner, automatically pausing collection 
 * when the UI is stopped and cancelling jobs upon scope destruction.
 */
fun <T> Flow<T>.collectSafely(
    lifecycleOwner: LifecycleOwner,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
): Job {
    return lifecycleOwner.lifecycleScopeLaunch {
        lifecycleOwner.repeatOnLifecycle(minActiveState) {
            collect { value ->
                action(value)
            }
        }
    }
}

private fun LifecycleOwner.lifecycleScopeLaunch(block: suspend CoroutineScope.() -> Unit): Job {
    return lifecycleScope.launch {
        block()
    }
}