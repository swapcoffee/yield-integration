package com.example.utils

import org.ton.java.tonlib.Tonlib
import org.ton.java.utils.Utils
import ru.tinkoff.kora.common.DefaultComponent

interface TonModule {

    @DefaultComponent
    fun tonlib(): Tonlib {
        return Tonlib.builder().testnet(false)
            .pathToTonlibSharedLib(
                Utils.getTonlibGithubUrl()
            )
            .build()


    }

}
