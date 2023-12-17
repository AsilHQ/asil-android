/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.shopping.fake

import org.halalz.fenix.shopping.middleware.NetworkChecker

class FakeNetworkChecker(
    private val isConnected: Boolean = true,
) : NetworkChecker {
    override fun isConnected(): Boolean = isConnected
}
