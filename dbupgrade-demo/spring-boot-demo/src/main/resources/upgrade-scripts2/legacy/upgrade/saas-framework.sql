--dbupgrade formatted sql
--changeset orivone:saas_framework_version_1.0.0
   CREATE TABLE IF NOT EXISTS `sa_module_menu` (
    `id` varchar(40) NOT NULL COMMENT 'id',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
    `update_by` varchar(40) DEFAULT NULL COMMENT '最后修改人',
    `update_time` datetime DEFAULT NULL COMMENT '最后修改时间',
    `is_del` char(1) DEFAULT '0' COMMENT '是否删除（0：否；1：是）',
    `version` int(11) DEFAULT '1' COMMENT '版本号',
    `name` varchar(40) DEFAULT NULL COMMENT '菜单名称',
    `url` varchar(1024) DEFAULT NULL COMMENT '跳转地址',
    `remark` varchar(200) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块菜单';

CREATE TABLE IF NOT EXISTS `sa_module_menu_button` (
    `id` varchar(40) NOT NULL COMMENT '唯一主键',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
    `update_by` varchar(40) DEFAULT NULL COMMENT '最后修改人',
    `update_time` datetime DEFAULT NULL COMMENT '最后修改时间',
    `version` int(11) DEFAULT '1' COMMENT '版本号',
    `is_del` char(1) DEFAULT '0' COMMENT '是否删除（0：否；1：是）',
    `name` varchar(120) DEFAULT NULL COMMENT '按钮名称',
    PRIMARY KEY (`id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模块菜单按钮';