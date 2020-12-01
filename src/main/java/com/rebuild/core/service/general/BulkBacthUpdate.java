/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.general;

import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import cn.devezhao.persist4j.record.JsonRecordCreator;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.service.DataSpecificationException;
import com.rebuild.core.support.general.BatchOperatorQuery;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 批量修改
 *
 * @author devezhao
 * @since 2019/12/2
 */
public class BulkBacthUpdate extends BulkOperator {

    private static final Logger LOG = LoggerFactory.getLogger(BulkBacthUpdate.class);

    /**
     * 修改为
     */
    public static final String OP_SET = "SET";
    /**
     * 置空
     */
    public static final String OP_NULL = "NULL";

    public BulkBacthUpdate(BulkContext context, GeneralEntityService ges) {
        super(context, ges);
    }

    @Override
    public Integer exec() {
        final ID[] willUpdates = prepareRecords();
        this.setTotal(willUpdates.length);

        JSONArray updateContents = context.getCustomData().getJSONArray("updateContents");
        // 转化成标准 FORM 格式
        JSONObject formJson = new JSONObject();
        for (Object o : updateContents) {
            JSONObject item = (JSONObject) o;
            String field = item.getString("field");
            String op = item.getString("op");
            String value = item.getString("value");

            if (OP_NULL.equalsIgnoreCase(op)) {
                formJson.put(field, StringUtils.EMPTY);
            } else if (OP_SET.equalsIgnoreCase(op)) {
                formJson.put(field, value);
            }
        }
        JSONObject metadata = new JSONObject();
        metadata.put("entity", context.getMainEntity().getName());
        formJson.put(JsonRecordCreator.META_FIELD, metadata);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Converter to : " + formJson);
        }

        for (ID id : willUpdates) {
            if (Application.getPrivilegesManager().allowUpdate(context.getOpUser(), id)) {
                // 更新记录
                formJson.getJSONObject(JsonRecordCreator.META_FIELD).put("id", id.toLiteral());

                try {
                    Record record = EntityHelper.parse(formJson, context.getOpUser());
                    ges.update(record);
                    this.addSucceeded();
                } catch (DataSpecificationException ex) {
                    LOG.warn("Cannot update : " + id + " Ex : " + ex);
                }

            } else {
                LOG.warn("No have privileges to UPDATE : " + context.getOpUser() + " > " + id);
            }
            this.addCompleted();
        }

        return getSucceeded();
    }

    @Override
    protected ID[] prepareRecords() {
        JSONObject customData = context.getCustomData();
        int dataRange = customData.getIntValue("_dataRange");
        BatchOperatorQuery query = new BatchOperatorQuery(dataRange, customData.getJSONObject("queryData"));
        return query.getQueryedRecords();
    }
}
