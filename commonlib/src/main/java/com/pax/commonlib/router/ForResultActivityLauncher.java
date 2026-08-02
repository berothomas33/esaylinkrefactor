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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import com.pax.commonlib.result.StartActivityLauncher;
import com.pax.commonlib.utils.LogUtils;
import com.sankuai.waimai.router.components.ActivityLauncher;
import com.sankuai.waimai.router.components.DefaultActivityLauncher;
import com.sankuai.waimai.router.core.Debugger;
import com.sankuai.waimai.router.core.UriRequest;
import com.sankuai.waimai.router.core.UriResult;

/**
 * File description
 *
 * @author yehongbo
 * @date 2021/12/20
 */
public class ForResultActivityLauncher extends DefaultActivityLauncher {
    private static final String TAG = "ForResultActivityLauncher";

    private ForResultActivityLauncher() {}

    private static class Holder {
        private static final ForResultActivityLauncher INSTANCE = new ForResultActivityLauncher();
    }

    public static ForResultActivityLauncher getInstance() { return Holder.INSTANCE; }

    @Override
    protected int startActivityByAction(@NonNull UriRequest request, @NonNull Intent intent,
            boolean internal) {
        return UriResult.CODE_NOT_FOUND;
    }

    @Override
    protected int startActivityByDefault(UriRequest request, @NonNull Context context,
            @NonNull Intent intent, Integer requestCode, boolean internal) {
        if (request instanceof ForResultUriRequest && context instanceof FragmentActivity) {
            StartActivityLauncher.OnResultCallback callback = ((ForResultUriRequest) request).getOnResultCallback();
            if (callback != null) {
                // Only when the request is initiated using ForResultUriRequest and the callback
                // is set through the onResult() method, the custom startup logic is used,
                // otherwise the default startup logic is used
                // 仅当使用 ForResultUriRequest 发起请求，并且通过 onResult() 方法设置了回调时，
                // 才使用自定义的启动逻辑，否则都使用默认的启动逻辑
                LogUtils.d(TAG, "Use custom start activity logic");
                try {
                    startForResult((FragmentActivity) context, intent, (resultCode, data) -> {
                        LogUtils.d(TAG, "Get result: " + resultCode);
                        callback.onResult(resultCode, data);
                    });
                    doAnimation(request);
                    request.putField(ActivityLauncher.FIELD_STARTED_ACTIVITY,
                            internal ? ActivityLauncher.INTERNAL_ACTIVITY : ActivityLauncher.EXTERNAL_ACTIVITY);
                    return UriResult.CODE_SUCCESS;
                } catch (ActivityNotFoundException activityNotFoundException) {
                    Debugger.w(activityNotFoundException);
                    return UriResult.CODE_NOT_FOUND;
                } catch (SecurityException securityException) {
                    Debugger.w(securityException);
                    return UriResult.CODE_FORBIDDEN;
                } catch (Exception exception) {
                    Debugger.w(exception);
                    return UriResult.CODE_ERROR;
                }
            }
        }
        LogUtils.d(TAG, "Use default start activity logic");
        return super.startActivityByDefault(request, context, intent, requestCode, internal);
    }

    private void startForResult(FragmentActivity activity, Intent intent,
            StartActivityLauncher.OnResultCallback callback) {
        ForResultFragment fragment = new ForResultFragment()
                .setIntent(intent)
                .setCallback(callback);
        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        if (fragment.isAdded()) {
            transaction.remove(fragment);
        }
        transaction.add(fragment, fragment.toString()).commitAllowingStateLoss();
    }
}
