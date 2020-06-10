/**
 * SPDX-License-Identifier: ISC
 * Copyright © 2014-2020 Bitmark. All rights reserved.
 * Use of this source code is governed by an ISC
 * license that can be found in the LICENSE file.
 */
package com.bitmark.autonomy.data.model

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName

data class ResourceRatingData(
    @Expose
    @SerializedName("resource")
    val resource: ResourceData,

    @Expose
    @SerializedName("score")
    val score: Int
) : Data