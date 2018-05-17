package org.tokend.template.features.explore

import android.app.ProgressDialog
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.kotlin.bindUntilEvent
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.BehaviorSubject
import kotlinx.android.synthetic.main.fragment_explore.*
import kotlinx.android.synthetic.main.include_error_empty_view.*
import kotlinx.android.synthetic.main.toolbar.*
import org.tokend.template.R
import org.tokend.template.base.fragments.BaseFragment
import org.tokend.template.base.fragments.ToolbarProvider
import org.tokend.template.base.logic.repository.assets.AssetsRepository
import org.tokend.template.base.logic.repository.balances.BalancesRepository
import org.tokend.template.base.logic.transactions.TxManager
import org.tokend.template.base.view.util.LoadingIndicatorManager
import org.tokend.template.features.explore.adapter.AssetListItem
import org.tokend.template.features.explore.adapter.AssetsAdapter
import org.tokend.template.util.Navigator
import org.tokend.template.util.ObservableTransformers
import org.tokend.template.util.ToastManager
import org.tokend.template.util.error_handlers.ErrorHandlerFactory

class ExploreAssetsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private val assetsRepository: AssetsRepository
        get() = repositoryProvider.assets()
    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val assetsAdapter = AssetsAdapter()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_explore, container, false)
    }

    override fun onInitAllowed() {
        toolbarSubject.onNext(toolbar)

        toolbar.title = getString(R.string.explore_title)

        initSwipeRefresh()
        initAssetsList()

        subscribeToAssets()
        subscribeToBalances()

        update()
    }

    // region Init
    private fun initSwipeRefresh() {
        swipe_refresh.setColorSchemeColors(ContextCompat.getColor(context!!, R.color.accent))
        swipe_refresh.setOnRefreshListener { update(force = true) }
    }

    private fun initAssetsList() {
        assets_recycler_view.layoutManager = LinearLayoutManager(context)
        assets_recycler_view.adapter = assetsAdapter

        error_empty_view.hide()
        error_empty_view.observeAdapter(assetsAdapter, R.string.no_assets_found)
        error_empty_view.setEmptyViewDenial { assetsRepository.isNeverUpdated }

        assetsAdapter.onItemClick { view, item ->
            if (view?.tag == "primary_action") {
                performPrimaryAssetAction(item)
            } else {
                openAssetDetails(view, item)
            }
        }
    }
    // endregion

    // region Assets
    private var assetsDisposable: CompositeDisposable? = null

    private fun subscribeToAssets() {
        assetsDisposable?.dispose()
        assetsDisposable = CompositeDisposable()
        assetsDisposable?.addAll(
                assetsRepository.itemsSubject
                        .flatMap { assets ->
                            balancesRepository.updateIfNotFreshDeferred()
                                    .onErrorComplete()
                                    .andThen(Observable.just(assets))
                        }
                        .compose(ObservableTransformers.defaultSchedulers())
                        .bindUntilEvent(lifecycle(), FragmentEvent.DESTROY_VIEW)
                        .subscribe { assets ->
                            displayAssets()
                        },
                assetsRepository.loadingSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .bindUntilEvent(lifecycle(), FragmentEvent.DESTROY_VIEW)
                        .subscribe {
                            loadingIndicator.setLoading(it, "assets")
                        },
                assetsRepository.errorsSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .bindUntilEvent(lifecycle(), FragmentEvent.DESTROY_VIEW)
                        .subscribe { error ->
                            if (!assetsAdapter.hasData) {
                                error_empty_view.showError(error) {
                                    update(true)
                                }
                            } else {
                                ErrorHandlerFactory.getDefault().handle(error)
                            }
                        }
        )
    }

    private fun displayAssets() {
        val balances = balancesRepository.itemsSubject.value
        val items = assetsRepository.itemsSubject.value
                .map { asset ->
                    AssetListItem(asset,
                            balances.find { it.asset == asset.code } != null)
                }
        assetsAdapter.setData(
                items
                        .sortedWith(Comparator { o1, o2 ->
                            return@Comparator o1.balanceExists.compareTo(o2.balanceExists)
                                    .takeIf { it != 0 }
                                    ?: o1.code.compareTo(o2.code)
                        })
        )
    }
    // endregion

    private var balancesDisposable: CompositeDisposable? = null
    private fun subscribeToBalances() {
        balancesDisposable?.dispose()
        balancesDisposable = CompositeDisposable(
                balancesRepository.loadingSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .bindUntilEvent(lifecycle(), FragmentEvent.DESTROY_VIEW)
                        .subscribe {
                            loadingIndicator.setLoading(it, "balances")
                        }
        )
    }

    private fun performPrimaryAssetAction(item: AssetListItem) {
        if (!item.balanceExists) {
            createBalance(item.code)
        } else {
            // TODO: Navigate to Wallet
        }
    }

    private fun openAssetDetails(view: View?, item: AssetListItem) {
        Navigator.openAssetDetails(activity!!, 314, item.source,
                cardView = view
        )
    }

    private fun createBalance(asset: String) {
        val progress = ProgressDialog(context)
        progress.isIndeterminate = true
        progress.setMessage(getString(R.string.processing_progress))
        progress.setCancelable(false)

        repositoryProvider.balances()
                .create(asset, accountProvider, repositoryProvider.systemInfo(),
                        TxManager(apiProvider))
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .bindUntilEvent(lifecycle(), FragmentEvent.DESTROY_VIEW)
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            ToastManager.short(getString(R.string.template_asset_balance_created,
                                    asset))
                            displayAssets()
                        },
                        onError = {
                            ErrorHandlerFactory.getDefault().handle(it)
                        }
                )
    }

    fun update(force: Boolean = false) {
        if (!force) {
            assetsRepository.updateIfNotFresh()
        } else {
            balancesRepository.invalidate()
            assetsRepository.update()
        }
    }
}