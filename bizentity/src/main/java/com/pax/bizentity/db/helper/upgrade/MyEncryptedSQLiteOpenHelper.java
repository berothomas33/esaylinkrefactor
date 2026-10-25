package com.pax.bizentity.db.helper.upgrade;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.Nullable;
import com.pax.bizentity.db.dao.DaoMaster;
import com.pax.commonlib.utils.LogUtils;
import net.sqlcipher.database.SQLiteOpenHelper;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.EncryptedDatabase;
public class MyEncryptedSQLiteOpenHelper extends BaseOpenHelper {
    private static final String TAG = "MyEncryptedSQLiteOpenHe";
    private final Context context;
    private final String name;

    public MyEncryptedSQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory) {
        super(context, name, factory);
        this.context = context;
        this.name = name;
    }

    @Override
    public void onUpgrade(Database db, int oldVersion, int newVersion) {
        LogUtils.e(TAG, "No migration path from schema " + oldVersion + " to " + newVersion + ", recreating tables");
        DaoMaster.dropAllTables(db, true);
        onCreate(db);
    }

    @Override
    public Database getEncryptedWritableDb(String password) {
        int version = DaoMaster.SCHEMA_VERSION;
        MyEncryptedHelper encryptedHelper = new MyEncryptedHelper(this.context, this.name, version, true);
        return encryptedHelper.wrap(encryptedHelper.getWritableDatabase(password));
    }

    private final class MyEncryptedHelper extends SQLiteOpenHelper {

        public MyEncryptedHelper(Context context, String name, int version, Boolean loadLibs) {
            super(context, name, null, version);
            if (loadLibs) {
                net.sqlcipher.database.SQLiteDatabase.loadLibs(context);
            }
        }

        @Override
        public void onCreate(net.sqlcipher.database.SQLiteDatabase sqLiteDatabase) {
            MyEncryptedSQLiteOpenHelper.this.onCreate(this.wrap(sqLiteDatabase));
        }

        @Override
        public void onUpgrade(net.sqlcipher.database.SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            MyEncryptedSQLiteOpenHelper.this.onUpgrade(this.wrap(sqLiteDatabase), oldVersion, newVersion);
        }

        @Override
        public void onOpen(net.sqlcipher.database.SQLiteDatabase db) {
            MyEncryptedSQLiteOpenHelper.this.onOpen(this.wrap(db));
        }

        public final Database wrap(@Nullable net.sqlcipher.database.SQLiteDatabase sqLiteDatabase) {
            return (Database) (new EncryptedDatabase(sqLiteDatabase));
        }
    }
}
