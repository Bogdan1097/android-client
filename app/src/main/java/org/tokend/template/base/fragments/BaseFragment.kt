package org.tokend.template.base.fragments

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.View
import io.reactivex.disposables.CompositeDisposable
import org.tokend.template.App
import org.tokend.template.base.activities.OnBackPressedListener
import org.tokend.template.base.logic.AppTfaCallback
import org.tokend.template.base.logic.di.providers.*
import org.tokend.template.util.ToastManager
import org.tokend.template.util.error_handlers.ErrorHandlerFactory
import javax.inject.Inject

abstract class BaseFragment : Fragment(), OnBackPressedListener {
    @Inject
    lateinit var appTfaCallback: AppTfaCallback
    @Inject
    lateinit var accountProvider: AccountProvider
    @Inject
    lateinit var apiProvider: ApiProvider
    @Inject
    lateinit var walletInfoProvider: WalletInfoProvider
    @Inject
    lateinit var repositoryProvider: RepositoryProvider
    @Inject
    lateinit var urlConfigProvider: UrlConfigProvider
    @Inject
    lateinit var errorHandlerFactory: ErrorHandlerFactory
    @Inject
    lateinit var toastManager: ToastManager

    override fun onBackPressed() = true

    protected lateinit var compositeDisposable: CompositeDisposable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (activity?.application as? App)?.stateComponent?.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        compositeDisposable = CompositeDisposable()
        if (savedInstanceState == null) {
            onInitAllowed()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.dispose()
    }

    abstract fun onInitAllowed()
}