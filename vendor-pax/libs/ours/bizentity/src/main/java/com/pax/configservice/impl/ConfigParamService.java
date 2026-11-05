package com.pax.configservice.impl;

import android.text.TextUtils;
import com.pax.commonlib.application.BaseApplication;
import com.pax.commonlib.sp.SharedPrefUtil;
import java.util.Map;

public class ConfigParamService {
    SharedPrefUtil defaultSp = new SharedPrefUtil(BaseApplication.getAppContext());

    public String getString(String key) {
        return defaultSp.getString(key);
    }

    public String getString(String key, String defaultValue) {
        return defaultSp.getString(key,defaultValue);
    }

    public void putString(String key, String value) {
        defaultSp.putString(key,value);
    }

    public void putInt(String key, int value) {
        defaultSp.putString(key, String.valueOf(value));
    }

    public int getInt(String key) {
        return getInt(key,-1);
    }

    public int getInt(String key, int defaultValue) {
        String value = defaultSp.getString(key);
        if (TextUtils.isEmpty(value)){
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    public void putBoolean(String key, boolean value) {
        defaultSp.putString(key, String.valueOf(value));
    }

    public boolean getBoolean(String key) {
        return getBoolean(key,false);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String value = defaultSp.getString(key);
        if (TextUtils.isEmpty(value)){
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public Map<String, String> getAll() {
        return (Map<String, String>) defaultSp.getAll();
    }
}
