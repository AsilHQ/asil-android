/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.shopping.fake

import org.halalz.fenix.shopping.ShoppingExperienceFeature

class FakeShoppingExperienceFeature(
    private val enabled: Boolean = true,
    private val productRecommendationsExposureEnabled: Boolean = true,
) : ShoppingExperienceFeature {

    override val isEnabled: Boolean
        get() = enabled

    override val isProductRecommendationsExposureEnabled: Boolean
        get() = productRecommendationsExposureEnabled
}