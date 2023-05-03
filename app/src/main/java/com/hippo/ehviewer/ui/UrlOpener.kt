/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.browser.customtabs.CustomTabsIntent
import androidx.navigation.NavController
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.parser.GalleryDetailUrlParser
import com.hippo.ehviewer.client.parser.GalleryListUrlParser
import com.hippo.ehviewer.client.parser.GalleryPageUrlParser
import com.hippo.ehviewer.ui.scene.GalleryDetailScene
import com.hippo.ehviewer.ui.scene.GalleryListScene
import com.hippo.ehviewer.ui.scene.ProgressScene
import eu.kanade.tachiyomi.ui.reader.ReaderActivity

object UrlOpener {
    fun openUrl(
        context: Context,
        url: String?,
        ehUrl: Boolean,
        galleryDetail: GalleryDetail? = null,
    ) {
        if (url.isNullOrEmpty()) {
            return
        }
        val intent: Intent
        val uri = Uri.parse(url)
        if (ehUrl) {
            if (galleryDetail != null) {
                val result = GalleryPageUrlParser.parse(url)
                if (result != null) {
                    if (result.gid == galleryDetail.gid) {
                        intent = Intent(context, ReaderActivity::class.java)
                        intent.action = ReaderActivity.ACTION_EH
                        intent.putExtra(ReaderActivity.KEY_GALLERY_INFO, galleryDetail)
                        intent.putExtra(ReaderActivity.KEY_PAGE, result.page)
                        context.startActivity(intent)
                        return
                    }
                } else if (url.startsWith("#c")) {
                    try {
                        intent = Intent(context, ReaderActivity::class.java)
                        intent.action = ReaderActivity.ACTION_EH
                        intent.putExtra(ReaderActivity.KEY_GALLERY_INFO, galleryDetail)
                        intent.putExtra(ReaderActivity.KEY_PAGE, url.replace("#c", "").toInt() - 1)
                        context.startActivity(intent)
                        return
                    } catch (_: NumberFormatException) {
                    }
                }
            }
        }
        val customTabsIntent = CustomTabsIntent.Builder()
        customTabsIntent.setShowTitle(true)
        try {
            customTabsIntent.build().launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser_installed, Toast.LENGTH_LONG).show()
        }
    }
}

@MainThread
fun NavController.jumpWithUrl(url: String): Boolean {
    if (url.isEmpty()) return false
    GalleryListUrlParser.parse(url)?.let { lub ->
        Bundle().apply {
            putString(GalleryListScene.KEY_ACTION, GalleryListScene.ACTION_LIST_URL_BUILDER)
            putParcelable(GalleryListScene.KEY_LIST_URL_BUILDER, lub)
            navigate(R.id.galleryListScene, this)
        }
        return true
    }

    GalleryDetailUrlParser.parse(url)?.apply {
        Bundle().apply {
            putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GID_TOKEN)
            putLong(GalleryDetailScene.KEY_GID, gid)
            putString(GalleryDetailScene.KEY_TOKEN, token)
            navigate(R.id.galleryDetailScene, this)
        }
        return true
    }

    GalleryPageUrlParser.parse(url)?.apply {
        Bundle().apply {
            putString(ProgressScene.KEY_ACTION, ProgressScene.ACTION_GALLERY_TOKEN)
            putLong(ProgressScene.KEY_GID, gid)
            putString(ProgressScene.KEY_PTOKEN, pToken)
            putInt(ProgressScene.KEY_PAGE, page)
            navigate(R.id.progressScene, this)
        }
        return true
    }
    return false
}
