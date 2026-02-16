package com.containerdashboard.data.datastore

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import okio.Path.Companion.toPath

interface CoroutinesComponent {
    val mainImmediateDispatcher: CoroutineDispatcher
    val applicationScope: CoroutineScope
}

internal class CoroutinesComponentImpl private constructor() : CoroutinesComponent {
    companion object {
        fun create(): CoroutinesComponent = CoroutinesComponentImpl()
    }

    override val mainImmediateDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
    override val applicationScope: CoroutineScope
        get() =
            CoroutineScope(
                SupervisorJob() + mainImmediateDispatcher,
            )
}

internal fun createDataStore(
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    migrations: List<DataMigration<Preferences>>,
    context: Any? = null,
    path: (context: Any?) -> String,
) = PreferenceDataStoreFactory.createWithPath(
    corruptionHandler = corruptionHandler,
    scope = coroutineScope,
    migrations = migrations,
    produceFile = {
        path(context).toPath()
    },
)

internal fun createDataStoreWithDefaults(
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>? = null,
    coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    migrations: List<DataMigration<Preferences>> = emptyList(),
    path: () -> String,
) = PreferenceDataStoreFactory
    .createWithPath(
        corruptionHandler = corruptionHandler,
        scope = coroutineScope,
        migrations = migrations,
        produceFile = {
            path().toPath()
        },
    )

expect fun dataStorePreferences(
    corruptionHandler: ReplaceFileCorruptionHandler<Preferences>?,
    coroutineScope: CoroutineScope,
    migrations: List<DataMigration<Preferences>>,
): DataStore<Preferences>

const val PREFERENCE_DATASTORE = "settings_preferences.preferences_pb"

interface DataStorePreferences : CoroutinesComponent {
    val instance: DataStore<Preferences>
}

internal class DataStorePreferencesImpl internal constructor() :
    DataStorePreferences,
    CoroutinesComponent by CoroutinesComponentImpl.create() {
        override val instance: DataStore<Preferences> =
            dataStorePreferences(
                null,
                applicationScope + Dispatchers.IO,
                emptyList(),
            )
    }

val dataStorePreferencesInstance get() = DataStorePreferencesImpl().instance
