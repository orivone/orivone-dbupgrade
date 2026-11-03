--dbupgrade formatted sql
-- ============================================================================
-- 达梦 DM · 1.0.0 首次安装基线（UpgradeVersion 的 install 标志 = true）
--
-- 先说清楚一件事：install 是注册表里的一个布尔参数，不是目录要求。
-- 这个文件放在 install/ 下只是命名习惯，框架从不解析目录名，
-- 只按注册表里写的完整路径去读。把它挪到 hotfix/、甚至 resources 根目录，
-- 只要注册表里的路径跟着改，照样能跑。
--
-- 这个文件做三件事：
--   1) 建三张演示表：客户、订单、心跳；
--   2) 灌两客户两订单的种子数据，固定主键 + 版本水位 = 天然只跑一次；
--   3) 用足本库的建表写法：沿用 Oracle 那套 NUMBER / VARCHAR2，只有驱动与连接串不同；DEFAULT 同样要在 NOT NULL 之前
-- ============================================================================

--changeset dm:create-demo-customer
CREATE TABLE demo_customer (
    id         NUMBER(19)    NOT NULL,
    name       VARCHAR2(64)  NOT NULL,
    status     NUMBER(3)     DEFAULT 1 NOT NULL,
    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_customer;

-- 订单表刻意不带 pay_status：那一列留给 1.1.0 加，
-- 好让「加列被自动判重」这件事在 demo 里真的发生一次。
--changeset dm:create-demo-order
CREATE TABLE demo_order (
    id          NUMBER(19)    NOT NULL,
    customer_id NUMBER(19)    NOT NULL,
    amount      NUMBER(12,2)  DEFAULT 0 NOT NULL,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_order;

-- 心跳表给 1.1.0 的 runAlways 用：每次执行刷新时间戳，行数不变。
-- 对照 1.2.0 那条普通 changeset 写的心跳，它只会留下第一次的时间。
--changeset dm:create-demo-heartbeat
CREATE TABLE demo_upgrade_heartbeat (
    id          NUMBER(19)    NOT NULL,
    version     VARCHAR2(32)  NOT NULL,
    executed_at TIMESTAMP     NOT NULL,
    PRIMARY KEY (id)
);
--rollback DROP TABLE demo_upgrade_heartbeat;

-- 基线只在空库上执行，所以种子不必自己判重；
-- 声明了 runAlways 的 changeset 才会反复执行，那种才必须幂等。
--changeset dm:seed-demo-customer
INSERT INTO demo_customer (id, name, status) VALUES (1, '张三', 1);
INSERT INTO demo_customer (id, name, status) VALUES (2, '李四', 1);
--rollback DELETE FROM demo_customer WHERE id IN (1, 2);

--changeset dm:seed-demo-order
INSERT INTO demo_order (id, customer_id, amount) VALUES (1, 1, 199.00);
INSERT INTO demo_order (id, customer_id, amount) VALUES (2, 2, 88.50);
--rollback DELETE FROM demo_order WHERE id IN (1, 2);
