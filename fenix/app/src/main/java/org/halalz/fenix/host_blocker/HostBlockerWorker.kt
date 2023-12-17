/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.halalz.fenix.host_blocker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class HostBlockerWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        HostBlockerManager.fetchAndOverwriteHostFile(applicationContext)
        SafeGazeBlockListManager.fetchAndOverwriteHostFile(applicationContext)
        return Result.success()
    }
}
