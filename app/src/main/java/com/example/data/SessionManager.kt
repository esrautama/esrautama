package com.example.data

import android.content.Context

class SessionManager(context: Context) {
    private val prefs = context.getSharedPreferences("sfa_prefs", Context.MODE_PRIVATE)

    fun saveSession(user: UserEntity) {
        prefs.edit().apply {
            putString("session_id", user.id)
            putString("session_username", user.username)
            putString("session_password", user.password)
            putString("session_role", user.role)
            putBoolean("session_is_stokis_sales", user.isStokisSales)
            apply()
        }
    }

    fun getSession(): UserEntity? {
        val id = prefs.getString("session_id", null) ?: return null
        val username = prefs.getString("session_username", "") ?: ""
        val password = prefs.getString("session_password", "") ?: ""
        val role = prefs.getString("session_role", "") ?: ""
        val isStokisSales = prefs.getBoolean("session_is_stokis_sales", false)
        return UserEntity(
            id = id,
            username = username,
            password = password,
            role = role,
            isStokisSales = isStokisSales
        )
    }

    fun clearSession() {
        prefs.edit().apply {
            remove("session_id")
            remove("session_username")
            remove("session_password")
            remove("session_role")
            remove("session_is_stokis_sales")
            apply()
        }
    }

    fun saveCartState(outletId: String?, cart: Map<String, Int>) {
        val serializedCart = cart.entries.joinToString(",") { "${it.key}:${it.value}" }
        prefs.edit().apply {
            putString("cart_outlet_id", outletId)
            putString("cart_items", serializedCart)
            apply()
        }
    }

    fun getSavedOutletId(): String? {
        return prefs.getString("cart_outlet_id", null)
    }

    fun getSavedCart(): Map<String, Int> {
        val serialized = prefs.getString("cart_items", null) ?: return emptyMap()
        if (serialized.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, Int>()
        try {
            val parts = serialized.split(",")
            for (part in parts) {
                val keyValue = part.split(":")
                if (keyValue.size == 2) {
                    val key = keyValue[0]
                    val value = keyValue[1].toIntOrNull()
                    if (value != null) {
                        map[key] = value
                    }
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return map
    }

    fun clearCartState() {
        prefs.edit().apply {
            remove("cart_outlet_id")
            remove("cart_items")
            apply()
        }
    }
}
