package org.tokend.template.features.explore

import android.app.Activity
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.support.transition.Fade
import android.support.transition.TransitionManager
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.support.v7.widget.Toolbar
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import io.reactivex.rxkotlin.addTo
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
import org.tokend.template.util.SearchUtil
import org.tokend.template.util.ToastManager
import org.tokend.template.util.error_handlers.ErrorHandlerFactory
import java.util.concurrent.TimeUnit


class ExploreAssetsFragment : BaseFragment(), ToolbarProvider {
    override val toolbarSubject: BehaviorSubject<Toolbar> = BehaviorSubject.create<Toolbar>()

    private val loadingIndicator = LoadingIndicatorManager(
            showLoading = { swipe_refresh.isRefreshing = true },
            hideLoading = { swipe_refresh.isRefreshing = false }
    )

    private var searchItem: MenuItem? = null

    private val assetsRepository: AssetsRepository
        get() = repositoryProvider.assets()
    private val balancesRepository: BalancesRepository
        get() = repositoryProvider.balances()

    private val assetsAdapter = AssetsAdapter()
    private var filter: String? = null
        set(value) {
            if (value != field) {
                field = value
                onFilterChanged()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_explore, container, false)
    }

    override fun onInitAllowed() {
        toolbarSubject.onNext(toolbar)

        toolbar.title = getString(R.string.explore_tokens_title)

        initSwipeRefresh()
        initAssetsList()
        initMenu()

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

        error_empty_view.observeAdapter(assetsAdapter, R.string.no_assets_found)
        error_empty_view.setEmptyViewDenial { assetsRepository.isNeverUpdated || balancesRepository.isNeverUpdated }

        assetsAdapter.onItemClick { view, item ->
            if (view?.tag == "primary_action") {
                performPrimaryAssetAction(item)
            } else {
                openAssetDetails(view, item)
            }
        }
    }

    private fun initMenu() {
        toolbar.inflateMenu(R.menu.explore)
        val menu = toolbar.menu

        searchItem = menu?.findItem(R.id.search) ?: return
        val searchView = searchItem?.actionView as? SearchView ?: return

        (searchView.findViewById(android.support.v7.appcompat.R.id.search_src_text) as? EditText)
                ?.apply {
                    setHintTextColor(ContextCompat.getColor(context!!, R.color.white_almost))
                    setTextColor(ContextCompat.getColor(context!!, R.color.white))
                }
        searchView.queryHint = getString(R.string.search)
        searchView.setOnQueryTextFocusChangeListener { _, focused ->
            if (!focused && searchView.query.isBlank()) {
                searchItem?.collapseActionView()
            }
        }

        RxSearchView.queryTextChanges(searchView)
                .skipInitialValue()
                .debounce(400, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    filter = it.trim().toString().takeIf { it.isNotEmpty() }
                }

        searchItem?.setOnMenuItemClickListener {
            TransitionManager.beginDelayedTransition(toolbar, Fade().setDuration(
                    resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
            ))
            searchItem?.expandActionView()
            true
        }

        searchItem?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean = true

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                filter = null
                return true
            }
        })
    }


    // endregion

    // region Assets
    private var assetsDisposable: CompositeDisposable? = null

    private fun subscribeToAssets() {
        assetsDisposable?.dispose()
        assetsDisposable = CompositeDisposable(
                Observable.zip(
                        assetsRepository.itemsSubject
                                .filter { assetsRepository.isFresh },
                        balancesRepository.itemsSubject
                                .filter { balancesRepository.isFresh },
                        BiFunction { _: Any, _: Any -> }
                )
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe {
                            displayAssets()
                        },
                assetsRepository.loadingSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe {
                            loadingIndicator.setLoading(it, "assets")
                        },
                assetsRepository.errorsSubject
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { error ->
                            if (!assetsAdapter.hasData) {
                                error_empty_view.showError(error, errorHandlerFactory.getDefault()) {
                                    update(true)
                                }
                            } else {
                                errorHandlerFactory.getDefault().handle(error)
                            }
                        }
        ).also { it.addTo(compositeDisposable) }
    }

    private fun displayAssets() {
        val storageUrl = urlConfigProvider.getConfig().storage
        val balances = balancesRepository.itemsSubject.value
        val items = assetsRepository.itemsSubject.value
                .map { asset ->
                    AssetListItem(
                            asset,
                            balances.find { it.asset == asset.code } != null,
                            storageUrl
                    )
                }
                .sortedWith(Comparator { o1, o2 ->
                    return@Comparator o1.balanceExists.compareTo(o2.balanceExists)
                            .takeIf { it != 0 }
                            ?: o1.code.compareTo(o2.code)
                })
                .let { items ->
                    filter?.let {
                        items.filter { item ->
                            SearchUtil.isMatchGeneralCondition(it, item.code, item.name)
                        }
                    } ?: items
                }


        assetsAdapter.setData(items)
    }

    private fun onFilterChanged() {
        displayAssets()
    }
    // endregion

    private var balancesDisposable: Disposable? = null
    private fun subscribeToBalances() {
        balancesDisposable?.dispose()
        balancesDisposable =
                balancesRepository.loadingSubject
                        .compose(ObservableTransformers.defaultSchedulers())
                        .subscribe {
                            loadingIndicator.setLoading(it, "balances")
                        }
                        .addTo(compositeDisposable)
    }

    private fun performPrimaryAssetAction(item: AssetListItem) {
        if (!item.balanceExists) {
            createBalanceWithConfirmation(item.code)
        } else {
            Navigator.openWallet(this, 0, item.code)
        }
    }

    private fun openAssetDetails(view: View?, item: AssetListItem) {
        Navigator.openAssetDetails(this, CREATE_REQUEST, item.source,
                cardView = view
        )
    }

    private fun createBalance(asset: String) {
        val progress = ProgressDialog(context)
        progress.isIndeterminate = true
        progress.setMessage(getString(R.string.processing_progress))
        progress.setCancelable(false)

        repositoryProvider.balances()
                .create(accountProvider, repositoryProvider.systemInfo(),
                        TxManager(apiProvider), asset)
                .compose(ObservableTransformers.defaultSchedulersCompletable())
                .doOnSubscribe {
                    progress.show()
                }
                .doOnTerminate {
                    progress.dismiss()
                }
                .subscribeBy(
                        onComplete = {
                            ToastManager(requireContext())
                                    .short(getString(R.string.template_asset_balance_created, asset))
                            displayAssets()
                        },
                        onError = {
                            errorHandlerFactory.getDefault().handle(it)
                        }
                )
    }

    private fun createBalanceWithConfirmation(asset: String) {
        AlertDialog.Builder(this.context!!, R.style.AlertDialogStyle)
                .setMessage(resources.getString(R.string.create_balance_confirmation, asset))
                .setPositiveButton(R.string.yes) { _, _ ->
                    createBalance(asset)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    fun update(force: Boolean = false) {
        if (!force) {
            balancesRepository.updateIfNotFresh()
            assetsRepository.updateIfNotFresh()
        } else {
            balancesRepository.update()
            assetsRepository.update()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == CREATE_REQUEST && resultCode == Activity.RESULT_OK) {
            update(true)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStart() {
        super.onStart()
        assets_recycler_view.isLayoutFrozen = false
    }

    override fun onStop() {
        super.onStop()
        assets_recycler_view.isLayoutFrozen = true
    }

    override fun onBackPressed(): Boolean {
        return searchItem?.isActionViewExpanded == false.also {
            searchItem?.collapseActionView()
        }
    }

    companion object {
        const val ID = 1114L
        const val CREATE_REQUEST = 314
    }
}