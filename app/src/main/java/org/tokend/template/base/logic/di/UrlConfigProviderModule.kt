package org.tokend.template.base.logic.di

import dagger.Module
import dagger.Provides
import org.tokend.template.base.logic.di.providers.UrlConfigProvider
import org.tokend.template.base.logic.di.providers.UrlConfigProviderFactory
import org.tokend.template.base.logic.model.UrlConfig
import javax.inject.Singleton

@Module
class UrlConfigProviderModule(
        private val defaultConfig: UrlConfig?
) {
    @Provides
    @Singleton
    fun urlConfigProvider(): UrlConfigProvider {
        return UrlConfigProviderFactory().createUrlConfigProvider()
                .also {
                    if (defaultConfig != null) {
                        it.setConfig(defaultConfig)
                    }
                }
    }
}