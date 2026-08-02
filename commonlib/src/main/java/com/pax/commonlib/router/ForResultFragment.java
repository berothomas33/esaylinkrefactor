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

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.pax.commonlib.result.StartActivityLauncher;
import com.pax.commonlib.utils.LogUtils;

/**
 * File description
 *
 * @author yehongbo
 * @date 2021/12/20
 */
public class ForResultFragment extends Fragment {
    private static final String TAG = "ForResultFragment";
    private final StartActivityLauncher launcher = new StartActivityLauncher(this);
    private Intent intent;
    private StartActivityLauncher.OnResultCallback callback;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launcher.launch(intent, (resultCode, data) -> {
            callback.onResult(resultCode, data);
            removeFragment();
        });
    }

    private void removeFragment() {
        try {
            getParentFragmentManager().beginTransaction().remove(this).commitAllowingStateLoss();
        } catch (Exception e) {
            LogUtils.e(TAG, "Remove Fragment Error", e);
        }
    }

    public ForResultFragment setIntent(Intent intent) {
        this.intent = intent;
        return this;
    }

    public ForResultFragment setCallback(StartActivityLauncher.OnResultCallback callback) {
        this.callback = callback;
        return this;
    }
}
