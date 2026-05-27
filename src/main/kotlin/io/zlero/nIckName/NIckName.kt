package io.zlero.nIckName

import io.zlero.cRFramework.CRPlugin
import kotlin.reflect.KClass

class NicknamePlugin : CRPlugin() {
    override fun components(): List<KClass<*>> = listOf(
        NicknameConfig::class,
        NicknameStorage::class,
        NicknameManager::class,
        NicknameCommand::class,
        CommandNicknameInterceptor::class
    )
}
