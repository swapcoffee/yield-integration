package com.example.repository

import com.example.dto.db.LiquidityPool
import com.example.dto.db.PoolStatsTradingVolume
import ru.tinkoff.kora.database.common.annotation.Query
import ru.tinkoff.kora.database.common.annotation.Repository
import ru.tinkoff.kora.database.jdbc.JdbcRepository

// language=postgresql
@Repository
interface PoolsRepository : JdbcRepository {

    // Методы для liquidity_pool

    @Query("SELECT * FROM liquidity_pool;")
    fun selectAllLiquidityPools(): List<LiquidityPool>

    @Query("SELECT * FROM liquidity_pool WHERE protocol = :protocol AND pool_address = :poolAddress;")
    fun selectLiquidityPool(protocol: String, poolAddress: String): LiquidityPool?

    @Query("SELECT * FROM liquidity_pool WHERE protocol = :protocol;")
    fun selectLiquidityPoolsByProtocol(protocol: String): List<LiquidityPool>

    @Query(
        """
        INSERT INTO liquidity_pool (
            protocol, 
            pool_address, 
            valid_from_utc_seconds, 
            valid_till_utc_seconds, 
            extra_data
        ) VALUES (
            :item.protocol,
            :item.poolAddress, 
            :item.validFromUtcSeconds, 
            :item.validTillUtcSeconds, 
            :item.extraData
        )
        ON CONFLICT (protocol, pool_address) DO UPDATE SET
            valid_from_utc_seconds = excluded.valid_from_utc_seconds,
            valid_till_utc_seconds = excluded.valid_till_utc_seconds,
            extra_data = excluded.extra_data;
    """
    )
    fun insertLiquidityPool(item: LiquidityPool)

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Query("SELECT * FROM pool_stats_trading_volume;")
    fun selectAllPoolsStatsTradingVolumes(): List<PoolStatsTradingVolume>

    @Query(
        """
        SELECT * FROM pool_stats_trading_volume 
        WHERE protocol = :protocol AND pool_address = :poolAddress AND trading_date = :tradingDate;
    """
    )
    fun selectPoolsStatsTradingVolume(
        protocol: String,
        poolAddress: String,
        tradingDate: Long
    ): PoolStatsTradingVolume?

    @Query(
        """
        SELECT * FROM pool_stats_trading_volume 
        WHERE protocol = :protocol 
        AND trading_date >= :tradingDateFrom
        AND trading_date <= :tradingDateTo;
    """
    )
    fun selectPoolsStatsTradingVolume(
        protocol: String,
        tradingDateFrom: Long,
        tradingDateTo: Long,
    ): List<PoolStatsTradingVolume>

    @Query("SELECT * FROM pool_stats_trading_volume WHERE protocol = :protocol AND pool_address = :poolAddress;")
    fun selectPoolsStatsTradingVolumesByPool(protocol: String, poolAddress: String): List<PoolStatsTradingVolume>

    @Query(
        """
        INSERT INTO pool_stats_trading_volume (
            protocol, 
            pool_address, 
            trading_date, 
            usd_volume_amount,
            interaction_count, 
            extra_data
        ) VALUES (
            :item.protocol, 
            :item.poolAddress, 
            :item.tradingDate, 
            :item.usdVolumeAmount,
            :item.interactionCount, 
            :item.extraData
        )
        ON CONFLICT (protocol, pool_address, trading_date) DO UPDATE SET
            usd_volume_amount = pool_stats_trading_volume.usd_volume_amount + excluded.usd_volume_amount,
            interaction_count = pool_stats_trading_volume.interaction_count + excluded.interaction_count,
            extra_data = excluded.extra_data;
    """
    )
    fun insertPoolsStatsTradingVolume(item: PoolStatsTradingVolume)
}