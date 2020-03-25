/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2019 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy

import com.bitmark.apiservice.configuration.GlobalConfiguration
import com.bitmark.apiservice.configuration.Network
import com.bitmark.autonomy.data.source.remote.api.middleware.BitmarkSdkHttpObserver
import com.bitmark.autonomy.data.source.remote.api.service.ServiceGenerator
import com.bitmark.autonomy.keymanagement.ApiKeyManager.Companion.API_KEY_MANAGER
import com.bitmark.autonomy.logging.Tracer
import com.bitmark.sdk.features.BitmarkSDK
import dagger.android.AndroidInjector
import dagger.android.support.DaggerApplication
import io.reactivex.plugins.RxJavaPlugins
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject

class AutonomyApplication : DaggerApplication() {

    companion object {
        private const val TAG = "AutonomyApplication"
    }

    @Inject
    lateinit var appLifecycleHandler: AppLifecycleHandler

    @Inject
    lateinit var bitmarkSdkHttpObserver: BitmarkSdkHttpObserver

    private val applicationInjector = DaggerAppComponent.builder()
        .application(this)
        .build()

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return applicationInjector
    }

    override fun onCreate() {
        super.onCreate()
        BitmarkSDK.init(buildBmSdkConfig())
        Sentry.init(AndroidSentryClientFactory(this))
        RxJavaPlugins.setErrorHandler { e ->
            Tracer.DEBUG.log(
                TAG,
                "intercept rx error ${e.javaClass} with message ${e.message} to be sent to thread uncaught exception"
            )
        }
    }

    private fun buildBmSdkConfig(): GlobalConfiguration.Builder {
        val builder = GlobalConfiguration.builder()
            .withConnectionTimeout(ServiceGenerator.CONNECTION_TIMEOUT.toInt())
            .withHttpObserver(bitmarkSdkHttpObserver)

        if (BuildConfig.DEBUG) {
            builder.withLogLevel(HttpLoggingInterceptor.Level.BODY)
        }
        if ("prd".equals(BuildConfig.FLAVOR)) {
            builder.withApiToken(API_KEY_MANAGER.bitmarkApiKey)
                .withNetwork(Network.LIVE_NET)
        } else {
            builder.withApiToken("bmk-lljpzkhqdkzmblhg")
                .withNetwork(Network.TEST_NET)
        }
        return builder
    }
}