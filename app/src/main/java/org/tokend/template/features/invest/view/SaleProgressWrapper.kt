package org.tokend.template.features.invest.view

import android.support.v4.content.ContextCompat
import android.text.SpannableString
import android.view.View
import kotlinx.android.synthetic.main.layout_sale_progress.view.*
import org.tokend.sdk.utils.BigDecimalUtil
import org.tokend.template.R
import org.tokend.template.base.view.util.AmountFormatter
import org.tokend.template.extensions.Sale
import org.tokend.template.extensions.highlight
import java.util.*
import kotlin.math.roundToInt

class SaleProgressWrapper(private val rootView: View) {
    fun displayProgress(sale: Sale) {
        val context = rootView.context
        val highlightColor = ContextCompat.getColor(context, R.color.accent)

        val investedAmountString = AmountFormatter.formatAssetAmount(sale.currentCap,
                sale.defaultQuoteAsset, abbreviation = true) + " ${sale.defaultQuoteAsset}"
        val investedString = context.getString(R.string.template_sale_invested, investedAmountString)

        val investedSpannableString = SpannableString(investedString)
        investedSpannableString.highlight(investedAmountString, highlightColor)
        rootView.sale_invested_text_view.text = investedSpannableString

        val scaledCurrentCap = BigDecimalUtil.scaleAmount(sale.currentCap,
                0).toInt()
        val scaledSoftCap = BigDecimalUtil.scaleAmount(sale.softCap,
                0).toInt()

        rootView.sale_progress.max = scaledSoftCap
        rootView.sale_progress.progress = scaledCurrentCap

        val percent = (scaledCurrentCap * 100f / scaledSoftCap).roundToInt()
        rootView.sale_progress_percent_text_view.text = "$percent%"

        if (sale.isAvailable || sale.isUpcoming) {
            rootView.sale_remain_time_text_view.visibility = View.VISIBLE

            val milliseconds =
                    if (sale.isAvailable)
                        (sale.endDate.time - Date().time)
                    else
                        (sale.startDate.time - Date().time)
            val days = Math.round(milliseconds / (1000f * 86400))

            val templateRes =
                    if (sale.isAvailable)
                        R.string.template_sale_days_to_go
                    else
                        R.string.template_sale_starts_in
            val daysString =
                    context.getString(templateRes, days, context.resources.getQuantityString(R.plurals.day, days))

            rootView.sale_remain_time_text_view.text = SpannableString(daysString)
                    .apply { highlight(days.toString(), highlightColor) }
        } else {
            rootView.sale_remain_time_text_view.visibility = View.GONE
        }

        val investorsCount = sale.statistics.investors
        val countString = "$investorsCount ${context.resources.getQuantityString(R.plurals.investor,
                investorsCount)}"
        rootView.sale_investors_count_text_view.text = SpannableString(countString)
                .apply { highlight(investorsCount.toString(), highlightColor) }
    }
}