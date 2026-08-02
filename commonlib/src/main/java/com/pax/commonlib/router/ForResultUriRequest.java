/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2021-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date                           Author                      Action
 * 2021/12/20                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.router;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.FragmentActivity;
import com.pax.commonlib.result.StartActivityLauncher;
import com.sankuai.waimai.router.activity.StartActivityAction;
import com.sankuai.waimai.router.common.DefaultUriRequest;
import com.sankuai.waimai.router.core.OnCompleteListener;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * File description
 *
 * @author yehongbo
 * @date 2021/12/20
 */
public class ForResultUriRequest extends DefaultUriRequest {
    private static final String ON_RESULT_CALLBACK = "StartActivityLauncher.OnResultCallback";

    public ForResultUriRequest(@NonNull FragmentActivity context,
            @NonNull Uri uri) {
        super(context, uri);
    }

    public ForResultUriRequest(@NonNull FragmentActivity context, @NonNull String uri) {
        super(context, uri);
    }

    public ForResultUriRequest(@NonNull FragmentActivity context, @NonNull String uri,
            HashMap<String, Object> extra) {
        super(context, uri, extra);
    }

    @Override
    public ForResultUriRequest onComplete(OnCompleteListener listener) {
        return (ForResultUriRequest) super.onComplete(listener);
    }

    @Override
    public ForResultUriRequest setErrorMessage(String message) {
        return (ForResultUriRequest) super.setErrorMessage(message);
    }

    @Override
    public ForResultUriRequest setResultCode(int resultCode) {
        return (ForResultUriRequest) super.setResultCode(resultCode);
    }

    @Override
    public ForResultUriRequest skipInterceptors() {
        return (ForResultUriRequest) super.skipInterceptors();
    }

    @Override
    public ForResultUriRequest appendParams(HashMap<String, String> params) {
        return (ForResultUriRequest) super.appendParams(params);
    }

    @Override
    public ForResultUriRequest tryStartUri(boolean value) {
        return (ForResultUriRequest) super.tryStartUri(value);
    }

    @Override
    public ForResultUriRequest from(int from) {
        return (ForResultUriRequest) super.from(from);
    }

    @Override
    public ForResultUriRequest activityRequestCode(int requestCode) {
        return (ForResultUriRequest) super.activityRequestCode(requestCode);
    }

    @Override
    public ForResultUriRequest overridePendingTransition(int enterAnim, int exitAnim) {
        return (ForResultUriRequest) super.overridePendingTransition(enterAnim, exitAnim);
    }

    @Override
    public ForResultUriRequest overrideStartActivity(StartActivityAction action) {
        return (ForResultUriRequest) super.overrideStartActivity(action);
    }

    @Override
    public ForResultUriRequest setIntentFlags(int flags) {
        return (ForResultUriRequest) super.setIntentFlags(flags);
    }

    @Override
    public ForResultUriRequest setActivityOptionsCompat(ActivityOptionsCompat options) {
        return (ForResultUriRequest) super.setActivityOptionsCompat(options);
    }

    @Override
    public ForResultUriRequest limitPackage(boolean limit) {
        return (ForResultUriRequest) super.limitPackage(limit);
    }

    @Override
    public ForResultUriRequest putExtra(String name, boolean value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, byte value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, char value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, short value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, int value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, long value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, float value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, double value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, String value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, CharSequence value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, Parcelable value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, Parcelable[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putIntentParcelableArrayListExtra(String name,
            ArrayList<? extends Parcelable> value) {
        return (ForResultUriRequest) super.putIntentParcelableArrayListExtra(name, value);
    }

    @Override
    public ForResultUriRequest putIntentIntegerArrayListExtra(String name, ArrayList<Integer> value) {
        return (ForResultUriRequest) super.putIntentIntegerArrayListExtra(name, value);
    }

    @Override
    public ForResultUriRequest putIntentStringArrayListExtra(String name, ArrayList<String> value) {
        return (ForResultUriRequest) super.putIntentStringArrayListExtra(name, value);
    }

    @Override
    public ForResultUriRequest putIntentCharSequenceArrayListExtra(String name,
            ArrayList<CharSequence> value) {
        return (ForResultUriRequest) super.putIntentCharSequenceArrayListExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, Serializable value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, boolean[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, byte[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, short[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, char[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, int[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, long[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, float[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, double[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, String[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, CharSequence[] value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtra(String name, Bundle value) {
        return (ForResultUriRequest) super.putExtra(name, value);
    }

    @Override
    public ForResultUriRequest putExtras(Bundle extras) {
        return (ForResultUriRequest) super.putExtras(extras);
    }

    public ForResultUriRequest onResult(StartActivityLauncher.OnResultCallback callback) {
        putField(ON_RESULT_CALLBACK, callback);
        return this;
    }

    @Nullable
    public StartActivityLauncher.OnResultCallback getOnResultCallback() {
        return getField(StartActivityLauncher.OnResultCallback.class, ON_RESULT_CALLBACK, null);
    }
}
