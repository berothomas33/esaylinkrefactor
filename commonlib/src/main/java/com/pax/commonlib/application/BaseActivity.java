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
 * Date                  Author	                 Action
 * 20190108  	         Steven.W                Create
 * ===========================================================================================
 */
package com.pax.commonlib.application;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import com.pax.commonlib.BuildConfig;
import com.pax.commonlib.R;
import com.pax.commonlib.application.quickclick.QuickClickProtection;
import com.pax.commonlib.utils.LogUtils;
import java.lang.reflect.Method;

/**
 * The type Base activity.
 */
public abstract class BaseActivity extends AppCompatActivity implements View.OnClickListener {

    /**
     * The constant TAG.
     */
    protected static final String TAG = "The Activity";
    /**
     * The Quick click protection.
     */
    protected QuickClickProtection quickClickProtection = QuickClickProtection.getInstance();
    private ActionBar mActionBar;

    //@Override
    //protected void attachBaseContext(Context newBase) {
    //    Locale locale = new Locale(Locale.getDefault().getLanguage(),
    //            CurrencyConverter.getDefCurrency().getCountry());
    //    super.attachBaseContext(LangUtils.getAttachBaseContext(newBase, locale));
    //}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(BuildConfig.RELEASE){
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);//禁用截屏
        }
        setContentView(getLayoutId());

        loadParam(); //AET-274

        mActionBar = getSupportActionBar();
        if (mActionBar != null) {
            mActionBar.setTitle(getTitleString());
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setDisplayShowTitleEnabled(true);
        }

        initViews();
        setListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        quickClickProtection.stop();
        LogUtils.i("onDestroy", "");
    }

    /**
     * get layout ID
     *
     * @return layout ID
     */
    protected abstract int getLayoutId();

    /**
     * views initial
     */
    protected abstract void initViews();

    /**
     * set listeners
     */
    protected abstract void setListeners();

    /**
     * load parameter
     */
    protected abstract void loadParam();

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (menu != null && menu.getClass().getSimpleName().equals("MenuBuilder")) {
            try {
                Method m = menu.getClass().getDeclaredMethod("setOptionalIconsVisible", Boolean.TYPE);
                m.setAccessible(true);
                m.invoke(menu, true);
            } catch (Exception e) {
                LogUtils.e(getClass().getSimpleName(), "onMenuOpened...unable to set icons for overflow menu", e);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    // AET-93
    @Override
    public final void onClick(View v) {
        if (quickClickProtection.isStarted()) {
            return;
        }
        quickClickProtection.start();
        onClickProtected(v);
    }

    /**
     * On click protected.
     *
     * @param v the v
     */
    protected void onClickProtected(View v) {
        //do nothing
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (quickClickProtection.isStarted()) { //AET-123
            return true;
        }
        quickClickProtection.start();
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return onKeyBackDown();
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * On key back down boolean.
     *
     * @return the boolean
     */
    protected boolean onKeyBackDown() {
        finish();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (quickClickProtection.isStarted()) { //AET-123
            return true;
        }
        quickClickProtection.start();
        return onOptionsItemSelectedSub(item);
    }

    /**
     * On options item selected sub boolean.
     *
     * @param item the item
     * @return the boolean
     */
    protected boolean onOptionsItemSelectedSub(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    /**
     * Gets title string.
     *
     * @return the title string
     */
    protected String getTitleString() {
        return getString(R.string.app_name);
    }

    /**
     * Enable back action.
     *
     * @param enableBack the enable back
     */
    protected void enableBackAction(boolean enableBack) {
        if (mActionBar != null) {
            mActionBar.setDisplayHomeAsUpEnabled(enableBack);
        }
    }

    /**
     * 设置是否显示ActionBar
     *
     * @param showActionBar true 显示 false 隐藏
     */
    protected void enableActionBar(boolean showActionBar) {
        if (mActionBar != null) {
            if (showActionBar) {
                mActionBar.show();
            } else {
                mActionBar.hide();
            }
        }
    }
}
