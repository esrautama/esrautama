package com.example.data

import java.security.MessageDigest
import kotlinx.coroutines.sync.Mutex

object SyncUtils {
    // Global mutex to prevent concurrent master data download and transaction uploading across ViewModel & SyncWorker
    val globalSyncMutex = Mutex()

    /**
     * Calculates a stable MD5 checksum of a List of Map data.
     * This ensures that even if the server returns all items,
     * the client only writes to the Room database when actual content changes.
     */
    fun calculateChecksum(dataList: List<Map<String, Any>>?): String {
        if (dataList.isNullOrEmpty()) return ""
        val stableString = dataList.map { map ->
            map.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }
        }.sorted().joinToString("\n")
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(stableString.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            stableString.hashCode().toString()
        }
    }
}
