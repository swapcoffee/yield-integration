--liquibase formatted sql
--changeset example:v1_tables dbms:postgresql encoding:UTF8

CREATE TABLE liquidity_pool
(
    protocol               VARCHAR(255) NOT NULL,
    pool_address           VARCHAR(255) NOT NULL,
    valid_from_utc_seconds BIGINT       NOT NULL,
    valid_till_utc_seconds BIGINT       NOT NULL,
    extra_data             TEXT         NOT NULL,
    PRIMARY KEY (protocol, pool_address)
);

CREATE TABLE pool_stats_trading_volume
(
    protocol          VARCHAR(255) NOT NULL,
    pool_address      VARCHAR(48)  NOT NULL,
    trading_date      BIGINT       NOT NULL, -- format: yyyyMMddHH -- example: 2025010215
    usd_volume_amount FLOAT        NOT NULL,
    interaction_count INT          NOT NULL,
    extra_data        TEXT         NOT NULL,
    PRIMARY KEY (protocol, pool_address, trading_date)
);