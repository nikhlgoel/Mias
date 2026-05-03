package dev.kid.core.agent.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dev.kid.core.agent.AgentCapability
import dev.kid.core.agent.capabilities.AppLaunchCapability
import dev.kid.core.agent.capabilities.CalculatorCapability
import dev.kid.core.agent.capabilities.ClipboardCapability
import dev.kid.core.agent.capabilities.DateTimeCapability
import dev.kid.core.agent.capabilities.FileSystemCapability
import dev.kid.core.agent.capabilities.WebFetchCapability
import dev.kid.core.agent.capabilities.WebResearchCapability
import dev.kid.core.agent.capabilities.MediaStoreFileGenerationCapability

/**
 * AgentModule — wires all AgentCapability implementations into a Hilt Set.
 *
 * The AgentOrchestrator constructor receives Set<AgentCapability> which
 * is automatically populated by Hilt's multibinding system.
 *
 * To add a new capability: add a @Binds @IntoSet method here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AgentModule {

    @Binds @IntoSet
    abstract fun bindCalculator(impl: CalculatorCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindClipboard(impl: ClipboardCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindDateTime(impl: DateTimeCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindFileSystem(impl: FileSystemCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindWebFetch(impl: WebFetchCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindWebResearch(impl: WebResearchCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindAppLaunch(impl: AppLaunchCapability): AgentCapability

    @Binds @IntoSet
    abstract fun bindFileGeneration(impl: MediaStoreFileGenerationCapability): AgentCapability
}
