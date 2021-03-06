/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2020 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy.feature.behavior.add

import androidx.lifecycle.Lifecycle
import com.bitmark.autonomy.data.source.BehaviorRepository
import com.bitmark.autonomy.feature.BaseViewModel
import com.bitmark.autonomy.util.livedata.CompositeLiveData
import com.bitmark.autonomy.util.livedata.RxLiveDataTransformer
import com.bitmark.autonomy.util.modelview.BehaviorModelView


class BehaviorAddingViewModel(
    lifecycle: Lifecycle,
    private val behaviorRepo: BehaviorRepository,
    private val rxLiveDataTransformer: RxLiveDataTransformer
) : BaseViewModel(lifecycle) {

    internal val addBehaviorLiveData = CompositeLiveData<BehaviorModelView>()

    fun addBehavior(newBehaviorData: NewBehaviorData) {
        addBehaviorLiveData.add(
            rxLiveDataTransformer.single(
                behaviorRepo.addBehavior(
                    newBehaviorData.title,
                    newBehaviorData.description
                ).map { id ->
                    BehaviorModelView(id, newBehaviorData.title, newBehaviorData.description)
                })
        )
    }

}