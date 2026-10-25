package com.pax.bizentity.db.helper;

import com.pax.bizentity.db.dao.DaoSession;
import com.pax.commonlib.utils.LogUtils;
import java.util.List;
import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.query.QueryBuilder;

public class BaseDaoHelper<T> {
    protected static final String TAG = "BaseDaoHelper";
    private final Class<T> entityClass;

    public BaseDaoHelper(Class<T> entityClass) {
        this.entityClass = entityClass;
    }

    static DaoSession getDaoSession() {
        DaoManager.getInstance().init();
        return DaoManager.getInstance().getDaoSession();
    }

    protected final AbstractDao<T, Long> getDao() {
        return (AbstractDao<T, Long>) getDaoSession().getDao(entityClass);
    }

    protected final QueryBuilder<T> getNoSessionQuery() {
        AbstractDao<T, Long> dao = getDao();
        dao.detachAll();
        return dao.queryBuilder();
    }

    protected final Database getDatabase() {
        return getDao().getDatabase();
    }

    public final boolean insert(T entity) {
        boolean success;
        try {
            long insert = this.getDao().insertOrReplace(entity);
            success = (int) insert != -1;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }
        return success;
    }

    public final boolean insert(List<T> entityList) {
        boolean success;
        try {
            this.getDao().insertOrReplaceInTx(entityList);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }

        return success;
    }

    public final boolean update(T entity) {
        boolean success;
        try {
            this.getDao().update(entity);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }

        return success;
    }

    public final boolean update(List<T> entityList) {
        boolean success;
        try {
            this.getDao().updateInTx(entityList);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }

        return success;
    }

    public final boolean delete(T entity) {
        boolean success;
        try {
            this.getDao().delete(entity);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }

        return success;
    }

    public final boolean deleteAll() {
        boolean success;
        try {
            this.getDao().deleteAll();
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }
        return success;
    }

    public final boolean deleteByKey(long key) {
        boolean success;
        try {
            this.getDao().deleteByKey(key);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }

        return success;
    }

    public final boolean deleteByKeyList(List<Long> keyList) {
        boolean success;
        try {
            this.getDao().deleteByKeyInTx(keyList);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }
        return success;
    }

    public final boolean deleteEntities(List<T> entityList) {
        boolean success;
        try {
            this.getDao().deleteInTx(entityList);
            success = true;
        } catch (Exception e) {
            LogUtils.e(TAG, e);
            success = false;
        }
        return success;
    }

    public final T loadByKey(long key) {
        return this.getDao().load(key);
    }

    public final T loadByRowId(long rowId) {
        return this.getDao().loadByRowId(rowId);
    }

    public final List<T> loadAll() {
        return this.getDao().loadAll();
    }
}
