/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.support;

/**
 * 系统配置项，所有配置应在此处声明
 *
 * @author devezhao
 * @since 12/25/2018
 */
public enum ConfigurationItem {

    // 系统指定
    SN, DBVer, AppBuild,
    // 数据目录（命令行指定）
    DataDirectory,
    // 缓存服务（安装/配置文件指定）
    CacheHost, CachePort, CacheUser, CachePassword,

    // 通用
    AppName("REBUILD"),
    LOGO,
    LOGOWhite,
    HomeURL("https://getrebuild.com/"),

    // 云存储
    StorageURL, StorageApiKey, StorageApiSecret, StorageBucket,

    // 邮件
    MailUser, MailPassword, MailAddr, MailName(AppName),
    MailSmtpServer,

    // 短信
    SmsUser, SmsPassword, SmsSign(AppName),

    // 开放注册
    OpenSignUp(true),

    // 登录背景图
    LiveWallpaper(true),

    // 启用文件分享
    FileSharable(true),

    // 启用页面水印
    MarkWatermark(false),

    // 密码策略，1-3
    PasswordPolicy(1),

    // 变更历史数据保留天数（0为禁用）
    RevisionHistoryKeepingDays(180),

    // 回收站数据保留天数（0为禁用）
    RecycleBinKeepingDays(180),

    // 启用数据库备份
    DBBackupsEnable(false),

    // 数据备份保留时间
    DBBackupsKeepingDays(180),

    // 允许同一用户多个会话
    MultipleSessions(true),

    // 默认语言
    DefaultLanguage("zh_CN"),

    // 数据脱敏（实验功能!!! 仅 PHONE/EMAIL 字段）
    DataMasking(false),

    // 激活 Redis 队列 (since = "2.1")
    @Deprecated
    RedisQueueEnable(false),

    // 视图页显示修改历史
    ShowViewHistory(true),

    // 登录验证码显示策略（1为自动，2为总是）
    LoginCaptchaPolicy(1),

    ;

    private Object defaultVal;

    ConfigurationItem() {
    }

    ConfigurationItem(Object defaultVal) {
        this.defaultVal = defaultVal;
    }

    /**
     * 默认值
     *
     * @return
     */
    public Object getDefaultValue() {
        if (defaultVal != null && defaultVal instanceof ConfigurationItem) {
            return ((ConfigurationItem) defaultVal).getDefaultValue();
        }
        return defaultVal;
    }
}
