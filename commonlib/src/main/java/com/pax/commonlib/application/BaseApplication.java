/*
 * ===========================================================================================
 * = COPYRIGHT
 *          PAX Computer Technology(Shenzhen) CO., LTD PROPRIETARY INFORMATION
 *   This software is supplied under the terms of a license agreement or nondisclosure
 *   agreement with PAX Computer Technology(Shenzhen) CO., LTD and may not be copied or
 *   disclosed except in accordance with the terms in that agreement.
 *     Copyright (C) 2019-? PAX Computer Technology(Shenzhen) CO., LTD All rights reserved.
 * Description: // Detail description about the function of this module,
 *             // interfaces with the other modules, and dependencies.
 * Revision History:
 * Date	                 Author	                Action
 * 20200318  	         xieYb                  Create
 * ===========================================================================================
 */
package com.pax.commonlib.application;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import androidx.multidex.MultiDex;
import com.pax.commonlib.BuildConfig;
import com.pax.commonlib.event.EventProxy;
import com.pax.commonlib.event.RxBusImpl;
import com.pax.commonlib.json.FastJson;
import com.pax.commonlib.json.JsonProxy;
import com.pax.commonlib.router.ForResultActivityLauncher;
import com.pax.commonlib.utils.LogUtils;
import com.pax.commonlib.utils.ThreadPoolManager;
import com.sankuai.waimai.router.Router;
import com.sankuai.waimai.router.common.DefaultRootUriHandler;
import com.sankuai.waimai.router.components.RouterComponents;
import com.sankuai.waimai.router.core.Debugger;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * provide application for all component
 */
public class BaseApplication extends Application {
    private static BaseApplication mBaseApplication ;
    private static Handler handler;
    private ExecutorService backgroundExecutor;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mBaseApplication = this;
        initWMRouter();
        ThreadPoolManager.getInstance().getExecutor().prestartAllCoreThreads();
        backgroundExecutor = ThreadPoolManager.getInstance().getExecutor();
        backgroundExecutor.execute(() -> {
            JsonProxy.getInstance().init(new FastJson());
            EventProxy.getInstance().init(new RxBusImpl());
            MultiDex.install(mBaseApplication);
        });
        handler = new Handler();
    }

    private void initWMRouter() {
        if (BuildConfig.DEBUG) {
            Debugger.setEnableDebug(true);
            Debugger.setEnableLog(true);
            Debugger.setLogger(new RouterDebugger());
        }
        RouterComponents.setActivityLauncher(ForResultActivityLauncher.getInstance());
        DefaultRootUriHandler rootHandler = new DefaultRootUriHandler(mBaseApplication);
        Router.init(rootHandler);
    }
    public static BaseApplication getAppContext(){
        return mBaseApplication;
    }

    public void runOnUiThread(final Runnable runnable) {
        handler.post(runnable);
    }

    public void runOnUiThreadDelay(final Runnable runnable, long delayMillis) {
        handler.postDelayed(runnable, delayMillis);
    }

    public void runInBackground(final Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    public <V> Future<V> runInBackground(final Callable<V> callable) {
        return backgroundExecutor.submit(callable);
    }

    private static class RouterDebugger implements Debugger.Logger {
        private static final String TAG = "WMRouter";

        @Override
        public void d(String msg, Object... args) {
            LogUtils.d(TAG, String.format(msg, args));
        }

        @Override
        public void i(String msg, Object... args) {
            LogUtils.i(TAG, String.format(msg, args));
        }

        @Override
        public void w(String msg, Object... args) {
            LogUtils.w(TAG, String.format(msg, args));
        }

        @Override
        public void w(Throwable t) {
            LogUtils.w(TAG, "", t);
        }

        @Override
        public void e(String msg, Object... args) {
            LogUtils.e(TAG, String.format(msg, args));
        }

        @Override
        public void e(Throwable t) {
            LogUtils.e(TAG, "", t);
        }

        @Override
        public void fatal(String msg, Object... args) {
            LogUtils.e(TAG, String.format(msg, args));
        }

        @Override
        public void fatal(Throwable t) {
            LogUtils.e(TAG, "", t);
        }
    }
}
