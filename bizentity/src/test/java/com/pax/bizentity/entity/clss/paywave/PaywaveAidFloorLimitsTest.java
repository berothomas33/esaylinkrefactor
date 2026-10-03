package com.pax.bizentity.entity.clss.paywave;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pax.bizentity.db.dao.PaywaveAidBeanDao;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.database.Database;
import org.junit.Test;

/** A Visa AID's per-transaction-type limits live in its own paywave_aid row (FLOOR_LIMITS). */
public class PaywaveAidFloorLimitsTest {

    private final FloorLimitsConverter converter = new FloorLimitsConverter();

    @Test
    public void limitsSurviveTheJsonColumn() {
        List<PayWaveInterFloorLimitBean> limits = Arrays.asList(
                new PayWaveInterFloorLimitBean((byte) 0x00, 0, 1000000000L, 60000, (byte) 1, (byte) 1, (byte) 1),
                new PayWaveInterFloorLimitBean((byte) 0x20, 0, 1000000000L, 0, (byte) 1, (byte) 0, (byte) 1));

        List<PayWaveInterFloorLimitBean> back =
                converter.convertToEntityProperty(converter.convertToDatabaseValue(limits));

        assertEquals(2, back.size());
        PayWaveInterFloorLimitBean sale = back.get(0);
        assertEquals(0x00, sale.getTransType());
        assertEquals(1000000000L, sale.getTransLimit());
        assertEquals(60000, sale.getCvmLimit());
        assertEquals(1, sale.getCvmLimitFlag());
        PayWaveInterFloorLimitBean refund = back.get(1);
        assertEquals(0x20, refund.getTransType());
        assertEquals(0, refund.getCvmLimitFlag());
    }

    @Test
    public void noLimitsIsNull() {
        assertNull(converter.convertToDatabaseValue(null));
        assertNull(converter.convertToEntityProperty(null));
        assertNull(converter.convertToEntityProperty(""));
    }

    @Test
    public void tableHasEveryDaoColumnInOrder() throws Exception {
        List<String> sql = new ArrayList<>();
        PaywaveAidBeanDao.createTable(capturing(sql), false);
        String ddl = sql.get(0);

        int last = -1;
        for (String column : daoColumns()) {
            int at = ddl.indexOf("\"" + column + "\"");
            assertTrue(column + " missing or out of order in " + ddl, at > last);
            last = at;
        }
        assertTrue(ddl.contains("\"FLOOR_LIMITS\" TEXT"));
    }

    private static List<String> daoColumns() throws IllegalAccessException {
        List<Property> properties = new ArrayList<>();
        for (Field f : PaywaveAidBeanDao.Properties.class.getFields()) {
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
