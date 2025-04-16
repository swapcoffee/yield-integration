import com.example.utils.RedisModule
import com.example.utils.TonModule
import com.example.utils.db.JdbcEnumModule
import ru.tinkoff.kora.application.graph.KoraApplication
import ru.tinkoff.kora.common.KoraApp
import ru.tinkoff.kora.config.hocon.HoconConfigModule
import ru.tinkoff.kora.database.jdbc.JdbcDatabaseModule
import ru.tinkoff.kora.database.jdbc.JdbcModule
import ru.tinkoff.kora.http.server.undertow.UndertowHttpServerModule
import ru.tinkoff.kora.json.module.JsonModule
import ru.tinkoff.kora.logging.logback.LogbackModule
import ru.tinkoff.kora.openapi.management.OpenApiManagementModule
import ru.tinkoff.kora.validation.module.ValidationModule

@KoraApp
interface Application :
    HoconConfigModule,
    UndertowHttpServerModule,
    JsonModule,
    ValidationModule,
    OpenApiManagementModule,
    LogbackModule,
    JdbcDatabaseModule,
    JdbcModule,
    RedisModule,
    TonModule,
    JdbcEnumModule

fun main() {
    KoraApplication.run { ApplicationGraph.graph() }
}