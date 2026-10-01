package com.pax.bizentity.db.helper.upgrade;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pax.bizentity.db.dao.PayPassAidBeanDao;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Schema 1 → 2 on a real SQLite database: paypass_aid gains MOBILE_SUPPORT / ACCOUNT_TYPE,
 * keeps its rows, and ends up with exactly the columns PayPassAidBeanDao reads and writes.
 */
public class SchemaMigrationsTest {

    private Connection connection;
    private Database db;

    @Before
    public void setUp() throws Exception {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        db = jdbcDatabase(connection);
    }

    @After
    public void tearDown() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void upgradesOneToTwoKeepingTheRows() throws Exception {
        createSchemaOneTable();
        exec("INSERT INTO paypass_aid (AID, KERNEL_CONFIG, SEL_FLAG, TRANS_LIMIT, TRANS_LIMIT_FLAG, "
                + "CVM_TRANS_LIMIT, FLOOR_LIMIT, FLOOR_LIMIT_FLAG, CVM_LIMIT, CVM_LIMIT_FLAG, "
                + "DATA_EXCHANGE_SUPPORT_FLAG, REFUND_VOID_FLOOR_LIMIT, SUPPORT_DEFAULT_MC_TERM_PARAM) "
                + "VALUES ('A0000000041010', '30', 0, 0, 0, 0, 0, 0, 60000, 1, 0, 0, 1)");

        assertTrue(SchemaMigrations.migrate(db, 1, 2));

        assertEquals(daoColumns(), tableColumns());
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT AID, KERNEL_CONFIG, CVM_LIMIT, MOBILE_SUPPORT, ACCOUNT_TYPE FROM paypass_aid")) {
            assertTrue(rs.next());
            assertEquals("A0000000041010", rs.getString(1));
            assertEquals("30", rs.getString(2));
            assertEquals(60000, rs.getLong(3));
            assertNull(rs.getString(4)); // filled by the next EMV parameter download
            assertNull(rs.getString(5));
            assertFalse(rs.next());
        }
    }

    @Test
    public void freshInstallTableMatchesTheDao() throws Exception {
        PayPassAidBeanDao.createTable(db, false);

        assertEquals(daoColumns(), tableColumns());
    }

    @Test
    public void noPathForSameOrOlderVersion() {
        assertFalse(SchemaMigrations.migrate(db, 2, 2));
        assertFalse(SchemaMigrations.migrate(db, 3, 2));
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    /** Today's CREATE TABLE minus the two schema-2 columns — what a version-1 install has. */
    private void createSchemaOneTable() throws SQLException {
        List<String> captured = new ArrayList<>();
        PayPassAidBeanDao.createTable(capturing(captured), false);
        String v2 = captured.get(0);
        String v1 = v2.replace(",\"MOBILE_SUPPORT\" TEXT", "").replace(",\"ACCOUNT_TYPE\" TEXT", "");
        assertFalse("schema-2 columns still in the v1 DDL", v1.contains("MOBILE_SUPPORT") || v1.contains("ACCOUNT_TYPE"));
        exec(v1);
    }

    private List<String> tableColumns() throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement st = connection.createStatement();
                ResultSet rs = st.executeQuery("PRAGMA table_info(paypass_aid)")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    /** PayPassAidBeanDao.Properties in ordinal order — the columns greenDAO binds and reads. */
    private static List<String> daoColumns() throws IllegalAccessException {
        List<Property> properties = new ArrayList<>();
        for (Field f : PayPassAidBeanDao.Properties.class.getFields()) {
            properties.add((Property) f.get(null));
        }
        properties.sort((a, b) -> Integer.compare(a.ordinal, b.ordinal));
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < properties.size(); i++) {
            assertEquals("ordinals must be contiguous", i, properties.get(i).ordinal);
            columns.add(properties.get(i).columnName);
        }
        return columns;
    }

    private void exec(String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute(sql);
        }
    }

    /** greenDAO's Database over JDBC — only execSQL is needed by createTable and the migrations. */
    private static Database jdbcDatabase(Connection connection) {
        return (Database) Proxy.newProxyInstance(Database.class.getClassLoader(),
                new Class<?>[] {Database.class}, (proxy, method, args) -> {
                    if (method.getName().equals("execSQL")) {
                        try (Statement st = connection.createStatement()) {
                            st.execute((String) args[0]);
                        }
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Database capturing(List<String> sql) {
        return (Database) Proxy.newProxyInstance(Database.class.getClassLoader(),
                new Class<?>[] {Database.class}, (proxy, method, args) -> {
                    if (method.getName().equals("execSQL")) {
                        sql.add((String) args[0]);
                        return null;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
