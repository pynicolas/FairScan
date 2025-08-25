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
package org.fairscan.app.view

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

const val PAGE_LIST_ELEMENT_SIZE_DP = 120

data class CommonPageListState(
    val document: DocumentUiModel,
    val onPageClick: (Int) -> Unit,
    val listState: LazyListState,
    val currentPageIndex: Int? = null,
    val onLastItemPosition: ((Offset) -> Unit)? = null,
)

@Composable
fun CommonPageList(
    state: CommonPageListState,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val content: LazyListScope.() -> Unit = {
        items(state.document.pageCount()) { index ->
            // TODO Use small images rather than big ones
            val image = state.document.load(index)
            if (image != null) {
                PageThumbnail(image, index, state)
            }
        }
    }
    if (isLandscape) {
        LazyColumn (
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
            content = content,
        )
    } else {
        LazyRow (
            state = state.listState,
            contentPadding = PaddingValues(4.dp),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
    if (state.document.isEmpty()) {
        Box(
            modifier = Modifier
                .height(120.dp)
                .addPositionCallback(state.onLastItemPosition, LocalDensity.current, 0.5f)
        ) {}
    }
}

@Composable
private fun PageThumbnail(
    image: Bitmap,
    index: Int,
    state: CommonPageListState,
) {
    val bitmap = image.asImageBitmap()
    val isSelected = index == state.currentPageIndex
    val borderColor =
        if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent
    val maxImageSize = PAGE_LIST_ELEMENT_SIZE_DP.dp
    var modifier =
        if (bitmap.height > bitmap.width)
            Modifier.height(maxImageSize)
        else
            Modifier.width(maxImageSize)
    if (index == state.document.lastIndex()) {
        val density = LocalDensity.current
        modifier = modifier.addPositionCallback(state.onLastItemPosition, density, 1.0f)
    }
    Box (modifier = Modifier.height(PAGE_LIST_ELEMENT_SIZE_DP.dp)) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .align(Alignment.Center)
                .padding(4.dp)
                .border(2.dp, borderColor)
                .clickable { state.onPageClick(index) }
        )
        Box(
            modifier = Modifier
                .padding(8.dp)
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp))
                .padding(vertical = 0.dp, horizontal = 8.dp)
        ) {
            Text(
                text = "${index + 1}",
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun Modifier.addPositionCallback(callback: ((Offset) -> Unit)?, density: Density, xFactor: Float): Modifier {
    if (callback == null) {
        return this
    }
    return this.onGloballyPositioned { coordinates ->
        with(density) {
            callback(coordinates.localToWindow(
                Offset(
                    x = PAGE_LIST_ELEMENT_SIZE_DP.dp.toPx() * xFactor,
                    y = PAGE_LIST_ELEMENT_SIZE_DP.dp.toPx() / 2)))
        }
    }
}
