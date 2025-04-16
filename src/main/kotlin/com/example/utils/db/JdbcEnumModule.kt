package com.example.utils.db

import com.example.dto.yield.YieldProtocols
import ru.tinkoff.kora.common.DefaultComponent
import ru.tinkoff.kora.database.jdbc.mapper.parameter.JdbcParameterColumnMapper
import ru.tinkoff.kora.database.jdbc.mapper.result.JdbcResultColumnMapper
import java.math.BigDecimal
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types

interface JdbcEnumModule {

    @DefaultComponent
    fun enumMapper(): JdbcResultColumnMapper<YieldProtocols> {
        return JdbcResultColumnMapper { rs: ResultSet, int: Int ->
            val value = rs.getString(int)
            if (rs.wasNull()) {
                return@JdbcResultColumnMapper null
            }
            YieldProtocols.resolve(value)
        }
    }

    @DefaultComponent
    fun enumMapper2(): JdbcParameterColumnMapper<YieldProtocols> {
        return JdbcParameterColumnMapper<YieldProtocols> { stmt: PreparedStatement, index: Int, o: YieldProtocols? ->
            if (o == null) {
                stmt.setNull(index, Types.VARCHAR)
            } else {
                stmt.setString(index, o.value)
            }
        }
    }

}