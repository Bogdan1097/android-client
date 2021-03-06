package org.tokend.template.base.activities

import android.os.Bundle
import io.reactivex.Completable
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_progress.*
import org.tokend.sdk.redirects.ClientRedirectPayload
import org.tokend.sdk.redirects.ClientRedirectType
import org.tokend.template.R
import org.tokend.template.extensions.toCompletable
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ToastManager

class ProcessLinkActivity : BaseActivity() {
    override val allowUnauthorized = true

    override fun onCreateAllowed(savedInstanceState: Bundle?) {
        setContentView(R.layout.activity_process_link)

        progress.show()

        progress.post {
            processIntentUrl()
        }
    }

    private fun processIntentUrl() {
        val intentData = intent?.data

        if (intentData == null) {
            finish()
            return
        }

        val url = intentData.toString()

        val payload = ClientRedirectPayload.fromUrl(url)
        if (payload != null && payload.isSuccessful
                && payload.type == ClientRedirectType.EMAIL_VERIFICATION) {
            performVerification(payload)
        } else {
            finish()
            return
        }
    }

    private fun performVerification(payload: ClientRedirectPayload) {
        val request =
                try {
                    apiProvider.getApi()
                            .wallets
                            .verify(payload)
                            .toCompletable()
                } catch (e: Exception) {
                    Completable.error(e)
                }

        request
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .subscribeBy(
                        onComplete = {
                            ToastManager(this).short(R.string.email_verified)
                            Navigator.toSignIn(this)
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                            finish()
                        }
                )
                .addTo(compositeDisposable)
    }
}
