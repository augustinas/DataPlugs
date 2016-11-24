package org.hatdex.dataplug.dao

import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointStatus, ApiEndpointVariant }

import scala.concurrent.Future

/**
 * Give access to the user object.
 */
trait DataPlugEndpointDAO {
  /**
   * Retrieves a user that matches the specified ID.
   *
   * @param phata The user phata.
   * @return The list of retrieved endpoints, as a String value
   */
  def retrievePhataEndpoints(phata: String): Future[Seq[ApiEndpointVariant]]

  /**
   * Retrieves a list of all registered endpoints together with corresponding user PHATAs
   *
   * @return The list of tuples of PHATAs and corresponding endpoints
   */
  def retrieveAllEndpoints: Future[Seq[(String, ApiEndpointVariant)]]

  /**
   * Activates an API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   */
  def activateEndpoint(phata: String, endpoint: String, variant: Option[String], configuration: Option[ApiEndpointCall]): Future[Unit]

  /**
   * Deactivates API endpoint for a user
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   */
  def deactivateEndpoint(phata: String, plugName: String, variant: Option[String]): Future[Unit]

  /**
   * Saves endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param endpoint Endpoint configuration
   */
  def saveEndpointStatus(phata: String, endpoint: ApiEndpointStatus): Future[Unit]

  /**
   * Fetch endpoint status for a given phata and plug endpoint variant
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveCurrentEndpointStatus(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointStatus]]

  /**
   * Retrieves most recent endpoint status for a given phata and plug endpoint
   *
   * @param phata The user phata.
   * @param plugName The plug endpoint name.
   * @param variant Endpoint variant to fetch status for
   * @return The available API endpoint configuration
   */
  def retrieveLastSuccessfulEndpointVariant(phata: String, plugName: String, variant: Option[String]): Future[Option[ApiEndpointVariant]]
}
