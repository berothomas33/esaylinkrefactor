package com.pax.bizentity.entity.clss.paywave;

import com.alibaba.fastjson.JSON;
import java.util.List;
import org.greenrobot.greendao.converter.PropertyConverter;

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
