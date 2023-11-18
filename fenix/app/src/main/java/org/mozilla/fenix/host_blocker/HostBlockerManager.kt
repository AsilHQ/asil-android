/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.host_blocker

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

object HostBlockerManager {
    fun fetchAndOverwriteHostFile(context: Context) {
        val localHostFile = File("${context.filesDir}/hosts.txt")
        if (localHostFile.parentFile?.exists() == false) {
            localHostFile.parentFile?.mkdirs()
            println("HostBlockerManager: Parent directory created for custom file path.")
        }

        val remoteHostFileURL = URL("https://storage.asil.co/hosts.txt")

        try {
            println("HostBlockerManager: Attempting to download host file from $remoteHostFileURL")

            val remoteHostFileData = remoteHostFileURL.readBytes()
            println("HostBlockerManager: Host file downloaded successfully.")

            FileOutputStream(localHostFile).use { fos ->
                fos.write(remoteHostFileData)
                println("HostBlockerManager: Host file overwritten to custom location.")
            }

            println("HostBlockerManager: Host file downloaded and overwritten successfully.")
        } catch (e: IOException) {
            println("HostBlockerManager: Error writing to the local host file: $e")
        }
    }

}
