package dev.flowicons.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Secure storage for the Flow Icons premium license key, backed by the IDE
 * [PasswordSafe] rather than plain settings XML.
 */
object LicenseStore {

    private val attributes: CredentialAttributes =
        CredentialAttributes(generateServiceName("Flow Icons", "license"))

    fun get(): String = PasswordSafe.instance.getPassword(attributes).orEmpty()

    fun set(key: String) {
        val trimmed = key.trim()
        PasswordSafe.instance.setPassword(attributes, trimmed.ifBlank { null })
    }

    val hasLicense: Boolean get() = get().isNotBlank()
}
