/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.service.trigger.impl;

import cn.devezhao.bizz.privileges.impl.BizzPermission;
import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.commons.ObjectUtils;
import cn.devezhao.persist4j.Field;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import cn.devezhao.persist4j.metadata.MissingMetaExcetion;
import cn.devezhao.persist4j.record.RecordVisitor;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.configuration.general.AutoFillinManager;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.easymeta.DisplayType;
import com.rebuild.core.metadata.easymeta.EasyDateTime;
import com.rebuild.core.metadata.easymeta.EasyField;
import com.rebuild.core.metadata.easymeta.EasyMetaFactory;
import com.rebuild.core.privileges.PrivilegesGuardContextHolder;
import com.rebuild.core.privileges.UserService;
import com.rebuild.core.service.ServiceSpec;
import com.rebuild.core.service.general.OperatingContext;
import com.rebuild.core.service.trigger.ActionContext;
import com.rebuild.core.service.trigger.ActionType;
import com.rebuild.core.service.trigger.TriggerException;
import com.rebuild.core.support.general.ContentWithFieldVars;
import com.rebuild.utils.CommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.Assert;

import java.util.*;

/**
 * 数据转写（自动更新）
 *
 * @author devezhao
 * @see AutoFillinManager
 * @since 2020/2/7
 */
@Slf4j
public class FieldWriteback extends FieldAggregation {

    private static final String DATE_EXPR = "#";

    private Set<ID> targetRecordIds;
    private Record targetRecordData;

    public FieldWriteback(ActionContext context) {
        super(context);
    }

    @Override
    public ActionType getType() {
        return ActionType.FIELDWRITEBACK;
    }

    @Override
    public void execute(OperatingContext operatingContext) throws TriggerException {
        List<ID> tschain = checkTriggerChain();
        if (tschain == null) return;

        this.prepare(operatingContext);
        if (targetRecordData.getAvailableFields().isEmpty()) return;

        final ServiceSpec targetService = MetadataHelper.isBusinessEntity(targetEntity)
                ? Application.getEntityService(targetEntity.getEntityCode())
                : Application.getService(targetEntity.getEntityCode());

        boolean tschainAdded = false;
        for (ID targetRecordId : targetRecordIds) {
            if (allowNoPermissionUpdate) {
                PrivilegesGuardContextHolder.setSkipGuard(targetRecordId);
            }
            // 如果当前用户对目标记录无修改权限
            else if (!Application.getPrivilegesManager().allow(operatingContext.getOperator(), targetRecordId, BizzPermission.UPDATE)) {
                log.warn("No permission to update record of target : {}", targetRecordId);
                continue;
            }

            // 会关联触发下一触发器
            if (!tschainAdded) {
                tschain.add(context.getConfigId());
                tschainAdded = true;
                TRIGGER_CHAIN_DEPTH.set(tschain);
            }

            Record targetRecord = targetRecordData.clone();
            targetRecord.setID(targetEntity.getPrimaryField().getName(), targetRecordId);
            targetService.update(targetRecord);
        }
    }

    @Override
    public void prepare(OperatingContext operatingContext) throws TriggerException {
        if (targetRecordIds != null) return;

        // FIELD.ENTITY
        String[] targetFieldEntity = ((JSONObject) context.getActionContent()).getString("targetEntity").split("\\.");
        sourceEntity = context.getSourceEntity();
        targetEntity = MetadataHelper.getEntity(targetFieldEntity[1]);

        targetRecordIds = new HashSet<>();

        // 自己
        if (SOURCE_SELF.equalsIgnoreCase(targetFieldEntity[0])) {
            targetRecordIds.add(context.getSourceRecord());
        } else {
            String sql = String.format("select %s from %s where %s = ?",
                    targetEntity.getPrimaryField().getName(), targetFieldEntity[1], targetFieldEntity[0]);
            Object[][] array = Application.getQueryFactory().createQueryNoFilter(sql)
                    .setParameter(1, operatingContext.getAnyRecord().getPrimary())
                    .array();

            for (Object[] o : array) {
                targetRecordIds.add((ID) o[0]);
            }
        }

        targetRecordData = prepareTargetRecordData();
    }

    private Record prepareTargetRecordData() {
        final Record record = EntityHelper.forNew(targetEntity.getEntityCode(), UserService.SYSTEM_USER, false);
        final JSONArray items = ((JSONObject) context.getActionContent()).getJSONArray("items");

        Set<String> fieldVars = new HashSet<>();
        for (Object o : items) {
            JSONObject item = (JSONObject) o;
            String sourceField = item.getString("sourceField");
            String updateMode = item.getString("updateMode");
            // fix: v2.2
            if (updateMode == null) {
                updateMode = sourceField.contains(DATE_EXPR) ? "FORMULA" : "FIELD";
            }

            if ("FIELD".equalsIgnoreCase(updateMode)) {
                fieldVars.add(sourceField);
            } else if ("FORMULA".equalsIgnoreCase(updateMode)) {
                if (sourceField.contains(DATE_EXPR)) {
                    fieldVars.add(sourceField.split(DATE_EXPR)[0]);
                } else {
                    Set<String> matchsVars = ContentWithFieldVars.matchsVars(sourceField);
                    for (String field : matchsVars) {
                        if (MetadataHelper.getLastJoinField(sourceEntity, field) == null) {
                            throw new MissingMetaExcetion(field, sourceEntity.getName());
                        }
                        fieldVars.add(field);
                    }
                }
            }
        }

        // 变量值
        Record useSourceData = null;
        if (!fieldVars.isEmpty()) {
            String sql = String.format("select %s from %s where %s = '%s'",
                    StringUtils.join(fieldVars, ","), sourceEntity.getName(),
                    sourceEntity.getPrimaryField().getName(), context.getSourceRecord());
            useSourceData = Application.createQueryNoFilter(sql).record();
        }

        for (Object o : items) {
            JSONObject item = (JSONObject) o;
            String targetField = item.getString("targetField");
            if (!MetadataHelper.checkAndWarnField(targetEntity, targetField)) {
                continue;
            }

            EasyField targetFieldEasy = EasyMetaFactory.valueOf(targetEntity.getField(targetField));

            String updateMode = item.getString("updateMode");
            String sourceField = item.getString("sourceField");

            // 置空
            if ("VNULL".equalsIgnoreCase(updateMode)) {
                record.setNull(targetField);
            }

            // 固定值
            else if ("VFIXED".equalsIgnoreCase(updateMode)) {
                RecordVisitor.setValueByLiteral(targetField, sourceField, record);
            }

            // 字段
            else if ("FIELD".equalsIgnoreCase(updateMode)) {
                Field sourceField2 = MetadataHelper.getLastJoinField(sourceEntity, sourceField);
                if (sourceField2 == null) continue;

                Object value = Objects.requireNonNull(useSourceData).getObjectValue(sourceField);
                Object newValue = value == null ? null : EasyMetaFactory.valueOf(sourceField2)
                        .convertCompatibleValue(value, targetFieldEasy);
                if (newValue != null) {
                    record.setObjectValue(targetField, newValue);
                }
            }

            // 公式
            else if ("FORMULA".equalsIgnoreCase(updateMode)) {
                Assert.notNull(useSourceData, "[useSourceData] not be null");

                // 日期兼容 fix: v2.2
                if (sourceField.contains(DATE_EXPR)) {
                    String fieldName = sourceField.split(DATE_EXPR)[0];
                    Field sourceField2 = MetadataHelper.getLastJoinField(sourceEntity, fieldName);
                    if (sourceField2 == null) continue;

                    Object value = useSourceData.getObjectValue(fieldName);
                    Object newValue = value == null ? null : ((EasyDateTime) EasyMetaFactory.valueOf(sourceField2))
                            .convertCompatibleValue(value, targetFieldEasy, sourceField);
                    if (newValue != null) {
                        record.setObjectValue(targetField, newValue);
                    }
                }

                // 公式
                else {
                    String clearFormual = sourceField.toUpperCase()
                            .replace("×", "*")
                            .replace("÷", "/")
                            .replace("`", "'");

                    for (String fieldName : useSourceData.getAvailableFields()) {
                        String replace = "{" + fieldName.toUpperCase() + "}";
                        if (clearFormual.contains(replace)) {
                            Object value = useSourceData.getObjectValue(fieldName);
                            if (value instanceof Date) {
                                value = CalendarUtils.getUTCDateTimeFormat().format(value);
                            } else {
                                value = value == null ? "0" : value.toString();
                            }
                            clearFormual = clearFormual.replace(replace, (String) value);
                        }
                    }

                    Object newValue = EvaluatorUtils.eval(clearFormual);
                    if (newValue != null) {
                        DisplayType dt = targetFieldEasy.getDisplayType();
                        if (dt == DisplayType.NUMBER) {
                            record.setLong(targetField, CommonsUtils.toLongHalfUp(newValue));
                        } else if (dt == DisplayType.DECIMAL) {
                            record.setDouble(targetField, ObjectUtils.toDouble(newValue));
                        } else if (dt == DisplayType.DATE || dt == DisplayType.DATETIME) {
                            record.setDate(targetField, (Date) newValue);
                        }
                    }
                }
            }
        }
        return record;
    }
}
