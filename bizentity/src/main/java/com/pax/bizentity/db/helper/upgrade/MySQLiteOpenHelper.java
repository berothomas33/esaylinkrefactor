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
 * 20190108  	         xieYb                   Create
 * ===========================================================================================
 */
package com.pax.bizentity.db.helper.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.pax.commonlib.utils.LogUtils;
import java.util.Locale;
import org.greenrobot.greendao.database.StandardDatabase;

/**
 * plain text database SQLiteOpenHelper
 */
public final class MySQLiteOpenHelper extends BaseOpenHelper {
    private static final String TAG = "MySQLiteOpenHelper";

    public MySQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
    }
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtils.d(TAG,String.format(Locale.US, "oldVersion:%s,newVersion:%s",oldVersion,newVersion));
        //upgrade all version
        for (int i = oldVersion;i<newVersion;i++){
            DbUpgrade.upgrade(new StandardDatabase(db),i,i+1);
        }
        LogUtils.e(TAG, "upgrade run success");

    }

    @Override
    public void afterDBReady() {
        DbUpgrade.executeAfterDBReadyCallback();
    }
}
