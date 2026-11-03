--dbupgrade formatted sql
-- ============================================================================
-- 版本 1.0.0 · 首次安装基线（install = true）
-- 说明：仅在数据库无版本记录时执行，用于建立最小可用表结构。
--      所有语句都按“可重跑”编写，配合执行器的 DDL 存在性判断，重跑不会报错。
-- ============================================================================

--changeset demo:create-demo-user
CREATE TABLE demo_user (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64)  NOT NULL                COMMENT '姓名',
    status     TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1 启用 / 0 停用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演示-用户表';
--rollback DROP TABLE demo_user;

--changeset demo:create-demo-order
CREATE TABLE demo_order (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT        NOT NULL                COMMENT '用户ID',
    amount     DECIMAL(12,2) NOT NULL DEFAULT 0.00   COMMENT '订单金额',
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_order_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演示-订单表';
--rollback DROP TABLE demo_order;

--changeset demo:create-demo-heartbeat
CREATE TABLE demo_upgrade_heartbeat (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    version     VARCHAR(32) NOT NULL                COMMENT '触发升级的版本号',
    executed_at DATETIME    NOT NULL                COMMENT '执行时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演示-runAlways 心跳表';
--rollback DROP TABLE demo_upgrade_heartbeat;

-- 首次安装的种子数据。DML 不会被自动判重，因此用 ON DUPLICATE KEY UPDATE 自保幂等
--changeset demo:seed-demo-user
INSERT INTO demo_user (id, name, status) VALUES (1, '张三', 1), (2, '李四', 1)
ON DUPLICATE KEY UPDATE name = name;
--rollback DELETE FROM demo_user WHERE id IN (1, 2);

--changeset demo:seed-demo-order
INSERT INTO demo_order (id, user_id, amount) VALUES (1001, 1, 199.00), (1002, 2, 88.50)
ON DUPLICATE KEY UPDATE amount = amount;
--rollback DELETE FROM demo_order WHERE id IN (1001, 1002);

-- 基线版本留痕：runAlways 的示例放在 1.1.0，这里只插一条固定记录说明基线执行时间
--changeset demo:heartbeat-baseline
INSERT INTO demo_upgrade_heartbeat (version, executed_at) VALUES ('1.0.0', NOW());
--rollback DELETE FROM demo_upgrade_heartbeat WHERE version = '1.0.0';
