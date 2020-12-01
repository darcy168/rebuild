/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.metadata.impl;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.PersistManagerFactory;
import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.rebuild.core.Application;
import com.rebuild.core.metadata.EntityHelper;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.privileges.AdminGuard;
import com.rebuild.core.service.BaseService;
import com.rebuild.core.service.ServiceSpec;
import org.springframework.stereotype.Service;

/**
 * 请使用协作类 {@link Entity2Schema}
 *
 * @author zhaofang123@gmail.com
 * @since 08/03/2018
 */
@Service
public class MetaEntityService extends BaseService implements AdminGuard {

    protected MetaEntityService(PersistManagerFactory aPMFactory) {
        super(aPMFactory);
    }

    @Override
    public int getEntityCode() {
        return EntityHelper.MetaEntity;
    }

    @Override
    public int delete(ID recordId) {
        Object[] entityRecord = getPersistManagerFactory().createQuery(
                "select entityName from MetaEntity where entityId = ?")
                .setParameter(1, recordId)
                .unique();
        final Entity delEntity = MetadataHelper.getEntity((String) entityRecord[0]);

        // 删除实体的相关配置
        // Field: belongEntity
        String[] confEntities = new String[]{
                "MetaField", "PickList", "LayoutConfig", "FilterConfig", "ShareAccess", "ChartConfig",
                "Attachment", "AutoFillinConfig", "RobotTriggerConfig", "RobotApprovalConfig",
                "DataReportConfig", "TransformConfig"
        };
        int del = 0;
        for (String conf : confEntities) {
            Entity confEntity = MetadataHelper.getEntity(conf);

            String ql = String.format("select %s from %s where belongEntity = ",
                    confEntity.getPrimaryField().getName(), confEntity.getName());
            if (confEntity.getEntityCode() == EntityHelper.Attachment) {
                ql += delEntity.getEntityCode();
            } else {
                ql += String.format("'%s'", delEntity.getName());

                if (confEntity.getEntityCode() == EntityHelper.TransformConfig) {
                    ql += String.format(" or targetEntity = '%s'", delEntity.getName());
                }
            }

            Object[][] usedArray = getPersistManagerFactory().createQuery(ql).array();

            ServiceSpec ss = Application.getService(confEntity.getEntityCode());
            for (Object[] used : usedArray) {
                ss.delete((ID) used[0]);
            }

            if (usedArray.length > 0) {
                LOG.warn("deleted configuration of entity [ " + delEntity.getName() + " ] in [ " + conf + " ] : " + usedArray.length);
            }
        }

        del += super.delete(recordId);
        return del;
    }

    @Override
    public Record update(Record record) {
        record = super.update(record);

        MetadataHelper.getMetadataFactory().refresh(false);
        return record;
    }

    /**
     * @see Entity2Schema
     */
    @Override
    public Record create(Record record) {
        return super.create(record);
    }
}
