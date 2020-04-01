/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2020 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy.feature.respondhelp

import android.os.Bundle
import androidx.lifecycle.Observer
import com.bitmark.autonomy.R
import com.bitmark.autonomy.feature.BaseAppCompatActivity
import com.bitmark.autonomy.feature.BaseViewModel
import com.bitmark.autonomy.feature.DialogController
import com.bitmark.autonomy.feature.Navigator
import com.bitmark.autonomy.feature.Navigator.Companion.RIGHT_LEFT
import com.bitmark.autonomy.feature.connectivity.ConnectivityHandler
import com.bitmark.autonomy.feature.requesthelp.Type
import com.bitmark.autonomy.feature.requesthelp.fromString
import com.bitmark.autonomy.feature.requesthelp.resId
import com.bitmark.autonomy.logging.Event
import com.bitmark.autonomy.logging.EventLogger
import com.bitmark.autonomy.util.DateTimeUtil
import com.bitmark.autonomy.util.ext.*
import com.bitmark.autonomy.util.modelview.HelpRequestModelView
import com.bitmark.autonomy.util.modelview.isResponded
import com.bitmark.autonomy.util.view.BottomAlertDialog
import kotlinx.android.synthetic.main.activity_respond_help.*
import javax.inject.Inject


class RespondHelpActivity : BaseAppCompatActivity() {

    companion object {
        private const val HELP_REQUEST = "help_request"

        fun getBundle(helpRequest: HelpRequestModelView) =
            Bundle().apply { putParcelable(HELP_REQUEST, helpRequest) }
    }

    @Inject
    internal lateinit var viewModel: RespondHelpViewModel

    @Inject
    internal lateinit var navigator: Navigator

    @Inject
    internal lateinit var dialogController: DialogController

    @Inject
    internal lateinit var logger: EventLogger

    @Inject
    internal lateinit var connectivityHandler: ConnectivityHandler

    private lateinit var helpRequest: HelpRequestModelView

    private var blocked = false

    override fun layoutRes(): Int = R.layout.activity_respond_help

    override fun viewModel(): BaseViewModel? = viewModel

    override fun initComponents() {
        super.initComponents()

        helpRequest =
            intent?.extras?.getParcelable(HELP_REQUEST) ?: error("missing required help request")

        if (helpRequest.isResponded()) {
            showRespondedState()
        } else {
            showPendingState()
        }

        tvTime.text = if (DateTimeUtil.isToday(helpRequest.createdAt)) {
            "%s - %s".format(
                getString(R.string.today),
                DateTimeUtil.stringToString(
                    helpRequest.createdAt,
                    newFormat = DateTimeUtil.TIME_FORMAT_1
                )
            )
        } else {
            DateTimeUtil.stringToString(helpRequest.createdAt)
        }

        tvType.setText(Type.fromString(helpRequest.subject).resId)
        tvExactNeed.text = helpRequest.exactNeeds
        tvLocation.text = helpRequest.meetingLocation
        tvContactInfo.text = helpRequest.contactInfo

        ivExactNeedCopy.setSafetyOnclickListener {
            copyToClipboard(helpRequest.exactNeeds)
            toast(getString(R.string.copied).toUpperCase())
        }

        ivLocationDirection.setSafetyOnclickListener {

        }

        ivContactInfoCopy.setSafetyOnclickListener {
            copyToClipboard(helpRequest.contactInfo)
            toast(getString(R.string.copied).toUpperCase())
        }

        layoutBack.setSafetyOnclickListener {
            navigator.anim(RIGHT_LEFT).finishActivity()
        }

        layoutSignUp.setSafetyOnclickListener {
            if (blocked) return@setSafetyOnclickListener
            viewModel.respondHelpRequest(helpRequest.id)
        }

    }

    override fun observe() {
        super.observe()

        viewModel.respondHelpRequestLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isSuccess() -> {
                    progressBar.gone()
                    val dialog = BottomAlertDialog(
                        this,
                        R.string.signed_up,
                        R.string.thank_u_for_signing_up,
                        R.string.the_community_member_in_need,
                        R.string.got_it
                    )
                    dialog.setOnDismissListener {
                        navigator.anim(RIGHT_LEFT).finishActivity()
                    }
                    dialog.show()
                    blocked = false
                }

                res.isError() -> {
                    progressBar.gone()
                    logger.logError(Event.HELP_REQUEST_RESPOND_ERROR, res.throwable())
                    if (connectivityHandler.isConnected()) {
                        dialogController.alert(R.string.error, R.string.could_not_sign_up_request)
                    } else {
                        dialogController.showNoInternetConnection()
                    }
                    blocked = false
                }

                res.isLoading() -> {
                    blocked = true
                    progressBar.visible()
                }
            }
        })
    }

    private fun showRespondedState() {
        ivBadgeCheck.visible()
        vDivider5.visible()
        vDivider6.visible()
        tvSignedUp.visible()
        layoutSignUp.gone()
    }

    private fun showPendingState() {
        ivBadgeCheck.gone()
        vDivider5.gone()
        vDivider6.gone()
        tvSignedUp.gone()
        layoutSignUp.visible()
    }

    override fun onBackPressed() {
        navigator.anim(RIGHT_LEFT).finishActivity()
        super.onBackPressed()
    }
}