/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.NinePatchDrawable;
import android.os.Build;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

import com.hippo.ehviewer.R;
import com.hippo.yorozuya.LayoutUtils;

public class ShadowLinearLayout extends LinearLayout {

    private NinePatchDrawable mShadow;
    private Rect mShadowPaddings = new Rect();
    private Rect mClipRect = new Rect();

    public ShadowLinearLayout(Context context) {
        super(context);
        init(context);
    }

    public ShadowLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ShadowLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // TODO not only 2dp
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setOutlineProvider(ViewOutlineProvider.BOUNDS);
            setElevation(LayoutUtils.dp2pix(context, 2));
        } else {
            setShadow((NinePatchDrawable) context.getResources().getDrawable(R.drawable.shadow_2dp));
        }
    }

    @Override
    public void setOutlineProvider(ViewOutlineProvider provider) {
        super.setOutlineProvider(provider);
    }

    private void setShadow(NinePatchDrawable shadow) {
        mShadow = shadow;
        mShadow.getPadding(mShadowPaddings);
        updateShadowBounds();
        setWillNotDraw(false);
    }

    private void updateShadowBounds() {
        NinePatchDrawable shadow = mShadow;
        if (shadow == null) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        Rect paddings = mShadowPaddings;
        if (width != 0 && height != 0) {
            shadow.setBounds(-paddings.left, -paddings.top, width + paddings.right, height + paddings.bottom);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        updateShadowBounds();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mShadow != null) {
            boolean needClip = false;
            Rect shadowPaddings = mShadowPaddings;
            Rect clipRect = mClipRect;
            canvas.getClipBounds(clipRect);

            if (-shadowPaddings.left < clipRect.left) {
                needClip = true;
                clipRect.left = -shadowPaddings.left;
            }
            if (-shadowPaddings.top < clipRect.top) {
                needClip = true;
                clipRect.top = -shadowPaddings.top;
            }
            if (getWidth() + shadowPaddings.right > clipRect.right) {
                needClip = true;
                clipRect.right = getWidth() + shadowPaddings.right;
            }
            if (getHeight() + shadowPaddings.bottom > clipRect.bottom) {
                needClip = true;
                clipRect.bottom = getHeight() + shadowPaddings.bottom;
            }

            int saved = 0;
            if (needClip) {
                saved = canvas.save();
                canvas.clipRect(clipRect, Region.Op.REPLACE);
            }

            mShadow.draw(canvas);

            if (needClip && saved != 0) {
                canvas.restoreToCount(saved);
            }
        }
        super.draw(canvas);
    }
}
