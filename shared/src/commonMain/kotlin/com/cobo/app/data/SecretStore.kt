package com.cobo.app.data

import com.cobo.app.platform.PlatformSecretStore

object SecretStore {
    fun seal(plain: String): String = PlatformSecretStore.seal(plain)
    fun open(raw: String): String? = PlatformSecretStore.open(raw)
}
