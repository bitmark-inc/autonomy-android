/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2019 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy.feature.main

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Bundle
import android.os.Handler
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.bitmark.autonomy.AppLifecycleHandler
import com.bitmark.autonomy.R
import com.bitmark.autonomy.feature.BaseAppCompatActivity
import com.bitmark.autonomy.feature.BaseViewModel
import com.bitmark.autonomy.feature.DialogController
import com.bitmark.autonomy.feature.Navigator
import com.bitmark.autonomy.feature.Navigator.Companion.BOTTOM_UP
import com.bitmark.autonomy.feature.Navigator.Companion.RIGHT_LEFT
import com.bitmark.autonomy.feature.areasearch.AreaSearchActivity
import com.bitmark.autonomy.feature.behavior.BehaviorReportActivity
import com.bitmark.autonomy.feature.connectivity.ConnectivityHandler
import com.bitmark.autonomy.feature.location.LocationService
import com.bitmark.autonomy.feature.notification.NotificationId
import com.bitmark.autonomy.feature.notification.NotificationPayloadType
import com.bitmark.autonomy.feature.splash.SplashActivity
import com.bitmark.autonomy.feature.symptoms.SymptomReportActivity
import com.bitmark.autonomy.logging.Event
import com.bitmark.autonomy.logging.EventLogger
import com.bitmark.autonomy.util.Constants.MAX_AREA
import com.bitmark.autonomy.util.DateTimeUtil
import com.bitmark.autonomy.util.ext.*
import com.bitmark.autonomy.util.modelview.AreaModelView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.layout_area_header.*
import javax.inject.Inject
import kotlin.math.roundToInt

class MainActivity : BaseAppCompatActivity() {

    companion object {

        private const val LOCATION_SETTING_CODE = 0xAE

        private const val NOTIFICATION_BUNDLE = "notification_bundle"

        private const val NOTIFICATION_ACTION_DELAY = 500L

        private const val SEARCH_REQUEST_CODE = 0x1A

        private const val AREA_LIST = "area_list"

        fun getBundle(notificationBundle: Bundle? = null, areas: List<AreaModelView>? = null) =
            Bundle().apply {
                if (notificationBundle != null) {
                    putBundle(
                        NOTIFICATION_BUNDLE,
                        notificationBundle
                    )
                }
                if (areas != null) {
                    putParcelableArrayList(AREA_LIST, ArrayList(areas))
                }
            }
    }

    @Inject
    internal lateinit var navigator: Navigator

    @Inject
    internal lateinit var dialogController: DialogController

    @Inject
    internal lateinit var viewModel: MainActivityViewModel

    @Inject
    internal lateinit var logger: EventLogger

    @Inject
    internal lateinit var connectivityHandler: ConnectivityHandler

    @Inject
    internal lateinit var locationService: LocationService

    @Inject
    internal lateinit var appLifecycleHandler: AppLifecycleHandler

    private val handler = Handler()

    private var notificationHandled = true

    private var currentAreaReady = false

    private val timezoneChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.updateTimezone(DateTimeUtil.getDefaultTimezone())
        }
    }

    private val locationChangeListener = object : LocationService.LocationChangedListener {
        override fun onLocationChanged(l: Location) {
            if (!appLifecycleHandler.isOnForeground()) {
                viewModel.updateLocation()
            }
            if (!currentAreaReady) {
                viewModel.getCurrentAreaProfile()
            }
        }

        override fun onPlaceChanged(place: String) {
            // do nothing
        }

    }

    private lateinit var areaList: MutableList<AreaModelView>

    private val adapter = AreaListRecyclerViewAdapter()

    private val actionListener = object : AreaListRecyclerViewAdapter.ActionListener {

        override fun onAreaDeleteClicked(id: String) {
            tvEditing.invisible()
            viewModel.delete(id)
        }

        override fun onAreaEditClicked(id: String) {
            adapter.setEditable(id, true)
            tvEditing.visible()
            Handler().postDelayed({
                showKeyBoard()
            }, 200)

            var keyBoardShowing = true
            detectKeyBoardState({ showing ->
                keyBoardShowing = showing
                // scroll to target pos
                val pos = adapter.getPos(id)
                if (pos != -1) {
                    rvAreas.smoothScrollToPosition(pos)
                }
                if (keyBoardShowing) return@detectKeyBoardState
                adapter.clearEditing()
                tvEditing.invisible()
                setAddAreaVisible(adapter.itemCount < MAX_AREA)
            }, { !keyBoardShowing })

        }

        override fun onAreaClicked(id: String?) {
            showArea(id)
        }

        override fun onDone(id: String, oldAlias: String, newAlias: String) {
            hideKeyBoard()
            tvEditing.invisible()
            adapter.setEditable(id, false)
            if (oldAlias == newAlias || newAlias.isEmpty()) {
                adapter.updateAlias(id, oldAlias)
            } else {
                viewModel.rename(id, newAlias)
            }

        }

    }

    override fun viewModel(): BaseViewModel? = viewModel

    override fun layoutRes(): Int = R.layout.activity_main

    override fun onStart() {
        super.onStart()
        if (!locationService.isPermissionGranted(this)) {
            locationService.requestPermission(this, grantedCallback = {
                startLocationService()

                // re-handle notification if it has not been handled correctly due to the permission requesting
                val notificationBundle = intent?.extras?.getBundle(NOTIFICATION_BUNDLE)
                if (notificationBundle != null && !notificationHandled) {
                    notificationHandled = handleNotification(notificationBundle)
                }
            }, permanentlyDeniedCallback = {
                dialogController.alert(
                    R.string.access_to_location_required,
                    R.string.autonomy_requires_access_to_your_location
                ) {
                    navigator.openAppSetting(this)
                }
            })
        }
    }

    private fun startLocationService() {
        locationService.start(this) { e ->
            e.startResolutionForResult(this, LOCATION_SETTING_CODE)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val notificationBundle = intent?.extras?.getBundle(NOTIFICATION_BUNDLE)
        val directFromNotification = intent?.extras?.getBoolean("direct_from_notification") == true
        if (notificationBundle != null && directFromNotification) {
            notificationHandled = handleNotification(notificationBundle)
        }
    }

    private fun registerTimezoneChangedReceiver() {
        val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
        registerReceiver(timezoneChangedReceiver, filter)
    }

    private fun unregisterTimezoneChangedReceiver() {
        try {
            unregisterReceiver(timezoneChangedReceiver)
        } catch (ignore: Throwable) {
        }
    }

    private fun handleNotification(notificationBundle: Bundle): Boolean {
        when (val notificationId = notificationBundle.getInt("notification_id")) {
            NotificationId.RISK_LEVEL_CHANGED -> {
                val areaId = notificationBundle.getString(NotificationPayloadType.POI_ID)
                handler.postDelayed({ showArea(areaId) }, NOTIFICATION_ACTION_DELAY)
                return true
            }
            NotificationId.ACCOUNT_SYMPTOM_FOLLOW_UP, NotificationId.ACCOUNT_SYMPTOM_SPIKE -> {
                if (!locationService.isPermissionGranted(this)) return false
                val symptoms =
                    notificationBundle.getStringArrayList(NotificationPayloadType.SYMPTOMS)
                val bundle = SymptomReportActivity.getBundle(symptoms)
                navigator.anim(RIGHT_LEFT).startActivity(SymptomReportActivity::class.java, bundle)
                return true
            }
            NotificationId.CLEAN_AND_DISINFECT, NotificationId.BEHAVIOR_REPORT_ON_SELF_HIGH_RISK, NotificationId.BEHAVIOR_REPORT_ON_RISK_AREA -> {
                if (!locationService.isPermissionGranted(this)) return false
                val behaviors =
                    notificationBundle.getStringArrayList(NotificationPayloadType.BEHAVIORS)
                val bundle = BehaviorReportActivity.getBundle(behaviors)
                navigator.anim(RIGHT_LEFT).startActivity(BehaviorReportActivity::class.java, bundle)
                return true
            }
            else -> error("unsupported notification id $notificationId")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                LOCATION_SETTING_CODE -> {
                    startLocationService()

                    // re-handle notification if it has not been handled correctly due to the location setting correct
                    val notificationBundle = intent?.extras?.getBundle(NOTIFICATION_BUNDLE)
                    if (notificationBundle != null && !notificationHandled) {
                        notificationHandled = handleNotification(notificationBundle)
                    }
                }

                SEARCH_REQUEST_CODE -> {
                    val area = AreaSearchActivity.extractResultData(data!!)
                    val existing = areaList.find { a -> a.location == area.location } != null
                    if (existing) return
                    areaList.add(area)
                    adapter.add(area)
                    setAddAreaVisible(adapter.itemCount < MAX_AREA)
                }
            }
        }
    }

    override fun initComponents() {
        super.initComponents()


        // handle notification
        val notificationBundle = intent?.extras?.getBundle(NOTIFICATION_BUNDLE)
        if (notificationBundle != null) {
            val directFromNotification =
                intent?.extras?.getBoolean("direct_from_notification") == true
            if (directFromNotification) {
                // start app again make sure all related logic handling
                val bundle = SplashActivity.getBundle(notificationBundle)
                navigator.anim(Navigator.FADE_IN)
                    .startActivityAsRoot(SplashActivity::class.java, bundle)
                return
            } else {
                notificationHandled = handleNotification(notificationBundle)
            }
        }

        // register components
        registerTimezoneChangedReceiver()
        if (locationService.isPermissionGranted(this)) {
            startLocationService()
        }
        locationService.addLocationChangeListener(locationChangeListener)

        if (!this::areaList.isInitialized) {
            areaList =
                intent?.extras?.getParcelableArrayList<AreaModelView>(AREA_LIST)?.toMutableList()
                    ?: error("missing area array")
        }


        val layoutManager = GridLayoutManager(this, 3)
        rvAreas.layoutManager = layoutManager
        setCurrentAreaScore(null)
        adapter.set(areaList)
        rvAreas.adapter = adapter
        rvAreas.isNestedScrollingEnabled = false
        (rvAreas.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false
        adapter.setActionListener(actionListener)
        setAddAreaVisible(adapter.itemCount < MAX_AREA)

        layoutAddArea.setSafetyOnclickListener {
            navigator.anim(BOTTOM_UP)
                .startActivityForResult(AreaSearchActivity::class.java, SEARCH_REQUEST_CODE)
        }
    }

    override fun deinitComponents() {
        locationService.removeLocationChangeListener(locationChangeListener)
        locationService.stop()
        unregisterTimezoneChangedReceiver()
        handler.removeCallbacksAndMessages(null)
        super.deinitComponents()
    }

    override fun observe() {
        super.observe()

        viewModel.deleteAreaLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isSuccess() -> {
                    progressBar.gone()
                    val id = res.data()!!
                    areaList.removeAll { a -> a.id == id }
                    adapter.remove(id)
                    setAddAreaVisible(adapter.itemCount < MAX_AREA)
                }

                res.isError() -> {
                    progressBar.gone()
                    logger.logError(Event.AREA_DELETE_ERROR, res.throwable())
                    if (connectivityHandler.isConnected()) {
                        dialogController.alert(R.string.error, R.string.could_not_delete_area)
                    } else {
                        dialogController.showNoInternetConnection()
                    }
                }

                res.isLoading() -> {
                    progressBar.visible()
                }
            }
        })

        viewModel.renameAreaLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isSuccess() -> {
                    progressBar.gone()
                    val data = res.data()!!
                    val id = data.first
                    val alias = data.second
                    areaList.find { a -> a.id == id }?.alias = alias
                    adapter.updateAlias(id, alias)
                }

                res.isError() -> {
                    progressBar.gone()
                    logger.logError(Event.AREA_RENAME_ERROR, res.throwable())
                    if (connectivityHandler.isConnected()) {
                        dialogController.alert(R.string.error, R.string.could_not_rename_area)
                    } else {
                        dialogController.showNoInternetConnection()
                    }
                }

                res.isLoading() -> {
                    progressBar.visible()
                }
            }
        })

        viewModel.updateTimezoneLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isError() -> {
                    logger.logError(Event.ACCOUNT_UPDATE_TIMEZONE_ERROR, res.throwable())
                }
            }
        })

        viewModel.updateLocationLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isError() -> {
                    logger.logError(Event.LOCATION_BACKGROUND_UPDATE_ERROR, res.throwable())
                }
            }
        })

        viewModel.getCurrentAreaScoreLiveData.asLiveData().observe(this, Observer { res ->
            when {
                res.isSuccess() -> {
                    progressBar.gone()
                    setCurrentAreaScore(res.data()!!)
                    currentAreaReady = true
                }

                res.isError() -> {
                    progressBar.gone()
                    logger.logError(Event.AREA_PROFILE_GETTING_ERROR, res.throwable())
                }

                res.isLoading() -> {
                    progressBar.visible()
                }
            }
        })
    }

    fun setAddAreaVisible(visible: Boolean) {
        if (visible) {
            layoutAddArea.visible()
        } else {
            layoutAddArea.invisible()
        }
    }

    private fun setCurrentAreaScore(score: Float?) {
        if (score == null) {
            ivHeaderScore.setImageResource(R.drawable.triangle_000)
            tvHeaderScore.text = "?"
        } else {
            val roundedScore = score.roundToInt()
            ivHeaderScore.setImageResource("triangle_%03d".format(if (roundedScore > 100) 100 else roundedScore))
            tvHeaderScore.text = roundedScore.toString()
        }
    }

    private fun showArea(id: String?) {
        // TODO navigate to corresponding screen
    }
}