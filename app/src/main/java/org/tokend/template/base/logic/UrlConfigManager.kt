package org.tokend.template.base.logic

import org.tokend.sdk.factory.GsonFactory
import org.tokend.template.base.logic.di.providers.UrlConfigProvider
import org.tokend.template.base.logic.model.UrlConfig
import org.tokend.template.base.logic.persistance.UrlConfigPersistor

/**
 * Manages network configuration of the app.
 */
class UrlConfigManager(
        private val urlConfigProvider: UrlConfigProvider,
        private val urlConfigPersistor: UrlConfigPersistor
) {
    private var listener: (() -> Unit)? = null

    fun setFromJson(jsonConfig: String): Boolean {
        return try {
            val config = GsonFactory().getBaseGson().fromJson(jsonConfig, UrlConfig::class.java)

            urlConfigProvider.setConfig(config)
            urlConfigPersistor.saveConfig(config)

            listener?.invoke()

            true
        } catch (e: Exception) {
            false
        }
    }

    fun get(): UrlConfig? {
        return if (urlConfigProvider.hasConfig())
            urlConfigProvider.getConfig()
        else
            null
    }

    fun onConfigUpdated(listener: () -> Unit) {
        this.listener = listener
    }
}