package com.beacon.admin.data.auth

object AuthSessionCleanupRegistry {
    private val cleanups = mutableSetOf<() -> Unit>()

    @Synchronized
    fun register(cleanup: () -> Unit): () -> Unit {
        cleanups += cleanup
        return {
            synchronized(this) {
                cleanups -= cleanup
            }
        }
    }

    @Synchronized
    fun clear() {
        cleanups.toList().forEach { it.invoke() }
        cleanups.clear()
    }
}
