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
 * 2021/12/17                     YeHongbo                    Create
 * ===========================================================================================
 */

package com.pax.commonlib.utils;

import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.result.MultiPermissionLauncher;
import java.util.Collections;

/**
 * Permission Utils
 */
public class PermissionUtils {

    private PermissionUtils() {
        // do nothing
    }

    private static class Holder {
        private static final PermissionUtils INSTANCE = new PermissionUtils();
    }

    public static PermissionUtils getInstance() {
        return Holder.INSTANCE;
    }

    public boolean isAllGranted(String[] permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(BaseApplication.getAppContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Request permissions.
     *
     * @param activity Activity
     * @param permissions Permissions
     * @param callback Request permissions result callback
     */
    public void request(@NonNull FragmentActivity activity,
            @NonNull String[] permissions,
            @Nullable MultiPermissionLauncher.OnGrantResultCallback callback) {
        if (isAllGranted(permissions)) {
            LogUtils.d("PermissionUtils", "Already granted");
            if (callback != null) {
                callback.onGrantResult(Collections.emptyList());
            }
            return;
        }
        LogUtils.d("PermissionUtils", "Create Fragment to receive result");
        GrantFragment fragment = new GrantFragment()
                .setPermissions(permissions)
                .setGrantResultCallback(callback);
        FragmentTransaction transaction = activity.getSupportFragmentManager().beginTransaction();
        if (fragment.isAdded()) {
            transaction.remove(fragment);
        }
        transaction.add(fragment, fragment.toString())
                .commitAllowingStateLoss();
    }

    /**
     * Request permissions.
     *
     * @param activity Activity
     * @param permission Single permission
     * @param callback Request permissions result callback
     */
    public void request(FragmentActivity activity, String permission,
            MultiPermissionLauncher.OnGrantResultCallback callback) {
        request(activity, new String[]{permission}, callback);
    }

    public static class GrantFragment extends Fragment {
        private static final String TAG = "GrantFragment";
        private String[] permissions;
        private MultiPermissionLauncher.OnGrantResultCallback resultCallback;
        private final MultiPermissionLauncher launcher = new MultiPermissionLauncher(this);

        @Override
        public void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            launcher.launch(permissions, denyList -> {
                if (resultCallback != null) {
                    resultCallback.onGrantResult(denyList);
                }
                removeFragment();
            });
        }

        private void removeFragment() {
            try {
                getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
                LogUtils.d(TAG, "remove fragment ok");
            } catch (Exception e) {
                LogUtils.e(TAG, "remove fragment failed", e);
            }
        }

        public GrantFragment setPermissions(@NonNull String[] permissions) {
            this.permissions = permissions;
            return this;
        }

        public GrantFragment setGrantResultCallback(@Nullable MultiPermissionLauncher.OnGrantResultCallback callback) {
            this.resultCallback = callback;
            return this;
        }
    }
}
