/*
 * Copyright (C) HAT Data Exchange Ltd - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 * Written by Andrius Aucinas <andrius.aucinas@hatdex.org>, 10 2016
 */

package org.hatdex.dataplugCalendar

import akka.actor.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Provides }
import com.mohiva.play.silhouette.api.Provider
import com.mohiva.play.silhouette.api.util.HTTPLayer
import com.mohiva.play.silhouette.impl.providers._
import com.mohiva.play.silhouette.impl.providers.oauth2.GoogleProvider
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.codingwell.scalaguice.ScalaModule
import org.hatdex.dataplug.actors.DataPlugManagerActor
import org.hatdex.dataplug.apiInterfaces.{ DataPlugOptionsCollector, DataPlugOptionsCollectorRegistry, DataPlugRegistry }
import org.hatdex.dataplug.controllers.{ DataPlugViewSet, DataPlugViewSetDefault }
import org.hatdex.dataplug.dao.{ DataPlugEndpointDAO, DataPlugEndpointDAOImpl }
import org.hatdex.dataplug.services.{ StartupService, StartupServiceImpl, _ }
import org.hatdex.dataplugCalendar.apiInterfaces.{ GoogleCalendarInterface, GoogleCalendarList }
import play.api.Configuration
import play.api.libs.concurrent.AkkaGuiceSupport

/**
 * The Guice module which wires all Silhouette dependencies.
 */
class Module extends AbstractModule with ScalaModule with AkkaGuiceSupport {

  /**
   * Configures the module.
   */
  def configure() {
    // Automatic database schema migrations
    bind[SchemaMigration].to[SchemaMigrationImpl]
    bind[SchemaMigrationLauncher].asEagerSingleton()
    bind[StartupService].to[StartupServiceImpl].asEagerSingleton()

    bind[DataPlugEndpointDAO].to[DataPlugEndpointDAOImpl]
    bind[DataPlugEndpointService].to[DataPlugEndpointServiceImpl]

    bind[DataPlugViewSet].to[DataPlugViewSetDefault]

    bindActor[DataPlugManagerActor]("dataplug-manager")
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleCalendarEndpoint The google calendar api endpoint implementation, injected
   * @return The DataPlugRegistry.
   */
  @Provides
  def provideDataPlugCollection(
    googleCalendarEndpoint: GoogleCalendarInterface): DataPlugRegistry = {

    DataPlugRegistry(Seq(
      googleCalendarEndpoint))
  }

  @Provides
  def provideDataPlugEndpointChoiceCollection(
    googleProvider: GoogleProvider,
    googleCalendarList: GoogleCalendarList): DataPlugOptionsCollectorRegistry = {

    val variants: Seq[(Provider, DataPlugOptionsCollector)] = Seq((googleProvider, googleCalendarList))
    DataPlugOptionsCollectorRegistry(variants)
  }

  /**
   * Provides the social provider registry.
   *
   * @param googleProvider The Google provider implementation.
   * @return The Silhouette environment.
   */
  @Provides
  def provideSocialProviderRegistry(
    googleProvider: GoogleProvider): SocialProviderRegistry = {

    SocialProviderRegistry(Seq(
      googleProvider))
  }

  /**
   * Provides the Google provider.
   *
   * @param httpLayer The HTTP layer implementation.
   * @param stateProvider The OAuth2 state provider implementation.
   * @param configuration The Play configuration.
   * @return The Google provider.
   */
  @Provides
  def provideGoogleProvider(
    httpLayer: HTTPLayer,
    stateProvider: OAuth2StateProvider,
    configuration: Configuration): GoogleProvider = {
    new GoogleProvider(httpLayer, stateProvider, configuration.underlying.as[OAuth2Settings]("silhouette.google"))
  }

  @Provides
  def providesAkkaActorScheduler(actorSystem: ActorSystem): Scheduler = {
    actorSystem.scheduler
  }
}