package org.tokend.template.base.logic.di

import dagger.Module
import dagger.Provides
import org.tokend.template.base.logic.di.providers.AccountProvider
import org.tokend.template.base.logic.di.providers.AccountProviderFactory
import javax.inject.Singleton

@Module
class AccountProviderModule {
    @Provides
    @Singleton
    fun accountProvider(): AccountProvider {
        return AccountProviderFactory().createAccountProvider()
    }
}