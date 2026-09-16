package de.danoeh.antennapod.ui.preferences.screen.synchronization

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncSettingsModule {

    @Binds
    abstract fun bindSyncSettingsRepository(impl: DefaultSyncSettingsRepository): SyncSettingsRepository
}
