/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2020 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy.feature.main

import androidx.lifecycle.Lifecycle
import com.bitmark.autonomy.data.source.UserRepository
import com.bitmark.autonomy.feature.BaseViewModel
import com.bitmark.autonomy.feature.auth.ServerAuthentication
import com.bitmark.autonomy.util.livedata.CompositeLiveData
import com.bitmark.autonomy.util.livedata.RxLiveDataTransformer
import com.bitmark.autonomy.util.modelview.AreaModelView


class MainActivityViewModel(
    lifecycle: Lifecycle,
    private val userRepo: UserRepository,
    private val rxLiveDataTransformer: RxLiveDataTransformer,
    private val serverAuth: ServerAuthentication
) : BaseViewModel(lifecycle) {

    internal val listAreaLiveData = CompositeLiveData<List<AreaModelView>>()

    fun listArea() {
        listAreaLiveData.add(rxLiveDataTransformer.single(userRepo.listArea().map { areas ->
            areas.map { a ->
                AreaModelView.newInstance(a)
            }
        }))
    }

    override fun onCreate() {
        super.onCreate()
        serverAuth.start()
    }

    override fun onDestroy() {
        serverAuth.stop()
        super.onDestroy()
    }
}