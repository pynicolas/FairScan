/*
 * Copyright 2025-2026 Pierre-Yves Nicolas
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app.ui.screens.about

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import org.fairscan.app.BuildConfig
import org.fairscan.app.ui.uriForFile
import java.io.File

const val EMAIL_ADDRESS = "contact@fairscan.org"

fun createContactEmailIntent(): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$EMAIL_ADDRESS".toUri()
    }

fun createEmailWithImageIntent(context: Context, imageFile: File?): Intent {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL_ADDRESS))
        putExtra(
            Intent.EXTRA_SUBJECT,
            "FairScan ${BuildConfig.VERSION_NAME}"
        )
        if (imageFile != null) {
            val uri = uriForFile(context, imageFile)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    return Intent.createChooser(intent, null)
}
