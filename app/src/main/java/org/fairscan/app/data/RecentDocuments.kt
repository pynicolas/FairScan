/*
 * Copyright 2025 Pierre-Yves Nicolas
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
package org.fairscan.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import org.fairscan.app.RecentDocuments
import java.io.InputStream
import java.io.OutputStream

object RecentDocumentsSerializer : Serializer<RecentDocuments> {
    override val defaultValue: RecentDocuments = RecentDocuments.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): RecentDocuments {
        return try {
            RecentDocuments.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", e)
        }
    }

    override suspend fun writeTo(
        t: RecentDocuments,
        output: OutputStream
    ) = t.writeTo(output)
}

val Context.recentDocumentsDataStore: DataStore<RecentDocuments> by dataStore(
    fileName = "recent_documents.pb",
    serializer = RecentDocumentsSerializer
)
