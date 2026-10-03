package com.pax.bizentity.entity.clss.paywave;

import com.alibaba.fastjson.JSON;
import java.util.List;
import org.greenrobot.greendao.converter.PropertyConverter;

/**
 * Stores a Visa AID's per-transaction-type limits in paywave_aid.FLOOR_LIMITS as a JSON array,
 * e.g. {@code [{"cvmLimit":60000,"cvmLimitFlag":1,...,"transType":0}, ...]}.
 */
public class FloorLimitsConverter implements PropertyConverter<List<PayWaveInterFloorLimitBean>, String> {

    @Override
    public List<PayWaveInterFloorLimitBean> convertToEntityProperty(String databaseValue) {
        if (databaseValue == null || databaseValue.isEmpty()) {
            return null;
        }
        return JSON.parseArray(databaseValue, PayWaveInterFloorLimitBean.class);
    }

    @Override
    public String convertToDatabaseValue(List<PayWaveInterFloorLimitBean> entityProperty) {
        return entityProperty == null ? null : JSON.toJSONString(entityProperty);
    }
}
