--dbupgrade formatted sql
--changeset orivone:system_version_1.0.2
CREATE TABLE IF NOT EXISTS `sys_department` (
                                  `id` varchar(40) NOT NULL COMMENT 'id',
                                  `name` varchar(150) DEFAULT NULL COMMENT '部门全称',
                                  `short_name` varchar(80) DEFAULT NULL COMMENT '部门简称',
                                  `pid` varchar(40) DEFAULT '0' COMMENT '上级部门（根节点）',
                                  `order_no` decimal(10,2) DEFAULT '0.00' COMMENT '排序',
                                  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                                  `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
                                  `update_by` varchar(40) DEFAULT NULL COMMENT '最后修改人',
                                  `update_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                                  `is_enable` char(1) DEFAULT '1' COMMENT '是否可用',
                                  `summary` varchar(1000) DEFAULT NULL COMMENT '描述',
                                  `version` int(11) DEFAULT '1' COMMENT '版本号',
                                  `is_del` char(1) DEFAULT '0' COMMENT '是否删除',
                                  PRIMARY KEY (`id`),
                                  KEY `pid` (`pid`),
                                  KEY `create_time` (`create_time`),
                                  KEY `update_time` (`update_time`),
                                  KEY `is_enable` (`is_enable`),
                                  KEY `order_no` (`order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE IF NOT EXISTS `sys_user` (
                            `id` varchar(40) NOT NULL COMMENT '主键',
                            `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
                            `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                            `update_by` varchar(40) DEFAULT NULL COMMENT '最后修改人',
                            `update_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                            `version` int(11) DEFAULT '1' COMMENT '版本号',
                            `is_del` char(1) DEFAULT '0' COMMENT '是否删除',
                            `name` varchar(40) DEFAULT NULL COMMENT '真实姓名',
                            `nick_name` varchar(80) DEFAULT NULL COMMENT '昵称',
                            `mobile` varchar(20) DEFAULT NULL COMMENT '移动电话号码',
                            `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
                            `sex` varchar(2) DEFAULT NULL COMMENT '性别（m：男；f：女)',
                            `card_code` varchar(32) DEFAULT NULL COMMENT '身份证号码',
                            `birthdate` date DEFAULT NULL COMMENT '出生日期',
                            `country_id` varchar(40) DEFAULT NULL COMMENT '国家',
                            `province_id` varchar(40) DEFAULT NULL COMMENT '省份',
                            `city_id` varchar(40) DEFAULT NULL COMMENT '城市',
                            `address` varchar(200) DEFAULT NULL COMMENT '居住地址',
                            `weixin` varchar(40) DEFAULT NULL COMMENT '微信号',
                            `qq` varchar(40) DEFAULT NULL COMMENT 'QQ号',
                            `is_enable` char(1) DEFAULT '1' COMMENT '是否可用',
                            `can_login` char(1) DEFAULT '1' COMMENT '是否可登录',
                            `remark` varchar(200) DEFAULT NULL COMMENT '备注',
                            `super_admin` char(1) DEFAULT '0' COMMENT '是否超管',
                            `user_no` varchar(40) DEFAULT NULL COMMENT '员工号',
                            `avatar` varchar(40) DEFAULT NULL COMMENT '头像',
                            PRIMARY KEY (`id`),
                            KEY `is_enable` (`is_enable`),
                            KEY `create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户基础信息';

CREATE TABLE IF NOT EXISTS `sys_menu_button` (
    `id` varchar(40) NOT NULL COMMENT '唯一主键',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `create_by` varchar(40) DEFAULT NULL COMMENT '创建人',
    `update_by` varchar(40) DEFAULT NULL COMMENT '最后修改人',
    `update_time` datetime DEFAULT NULL COMMENT '最后修改时间',
    `version` int(11) DEFAULT '1' COMMENT '版本号',
    `is_del` char(1) DEFAULT '0' COMMENT '是否删除',
    `name` varchar(120) DEFAULT NULL COMMENT '按钮名称',
    `front_permission_code` varchar(100) DEFAULT NULL COMMENT '前端权限代码',
    `pid` varchar(40) DEFAULT NULL COMMENT '所属菜单',
    `remark` varchar(200) DEFAULT NULL COMMENT '备注',
    `is_enable` char(1) DEFAULT '1' COMMENT '是否可用（0：否；1：是）',
    `order_no` decimal(10,2) DEFAULT '0.00' COMMENT '排序',
    `permission_code` varchar(200) DEFAULT NULL COMMENT '后端权限代码多个用英文封号隔开',
    PRIMARY KEY (`id`),
    KEY `is_del` (`is_del`) USING BTREE,
    KEY `is_enable` (`is_enable`) USING BTREE,
    KEY `pid` (`pid`) USING BTREE,
    KEY `order_no` (`order_no`) USING BTREE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单按钮';

--changeset orivone:system_version_1.0.3
ALTER TABLE `sys_department`
    MODIFY COLUMN `is_enable`  char(1) NULL DEFAULT '1' COMMENT '是否可用' ,
    MODIFY COLUMN `version`  int(11) NULL DEFAULT '1' COMMENT '版本号' ,
    MODIFY COLUMN `is_del`  char(1) NULL DEFAULT '0' COMMENT '是否删除' ;

ALTER TABLE `sys_user`
    MODIFY COLUMN `version`  int(11) NULL DEFAULT '1' COMMENT '版本号',
    MODIFY COLUMN `is_del`  char(1) NULL DEFAULT '0' COMMENT '是否删除',
    MODIFY COLUMN `is_enable`  char(1) NULL DEFAULT '1' COMMENT '是否可用',
    MODIFY COLUMN `can_login`  char(1) NULL DEFAULT '1' COMMENT '是否可登录',
    MODIFY COLUMN `super_admin`  char(1) NULL DEFAULT '0' COMMENT '是否超管';





