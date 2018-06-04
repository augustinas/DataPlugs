package org.hatdex.dataplugInstagram.apiInterfaces

import akka.Done
import akka.actor.{ ActorRef, Scheduler }
import akka.util.Timeout
import com.google.inject.Inject
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.impl.providers.OAuth2Info
import com.mohiva.play.silhouette.impl.providers.oauth2.InstagramProvider
import org.hatdex.dataplug.utils.FutureTransformations
import org.hatdex.dataplug.actors.Errors.SourceDataProcessingException
import org.hatdex.dataplug.apiInterfaces.DataPlugEndpointInterface
import org.hatdex.dataplug.apiInterfaces.authProviders.{ OAuth2TokenHelper, RequestAuthenticatorOAuth2 }
import org.hatdex.dataplug.apiInterfaces.models.{ ApiEndpointCall, ApiEndpointMethod }
import org.hatdex.dataplug.services.UserService
import org.hatdex.dataplug.utils.Mailer
import org.hatdex.dataplugInstagram.models.InstagramMedia
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSClient

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class InstagramFeedInterface @Inject() (
    val wsClient: WSClient,
    val userService: UserService,
    val authInfoRepository: AuthInfoRepository,
    val tokenHelper: OAuth2TokenHelper,
    val mailer: Mailer,
    val scheduler: Scheduler,
    val provider: InstagramProvider) extends DataPlugEndpointInterface with RequestAuthenticatorOAuth2 {

  val namespace: String = "instagram"
  val endpoint: String = "feed"
  protected val logger: Logger = Logger(this.getClass)

  val defaultApiEndpoint = InstagramFeedInterface.defaultApiEndpoint

  val refreshInterval = 1.hour

  def buildContinuation(content: JsValue, params: ApiEndpointCall): Option[ApiEndpointCall] = {
    logger.debug("Building continuation...")

    val maybeMinIdParam = params.pathParameters.get("min_id")

    if (maybeMinIdParam.isDefined) {
      None
    }
    else {
      val maybeNextMaxId = (content \ "pagination" \ "next_max_id").asOpt[String]
      val maybeMinIdStorage = params.storage.get("min_id")

      (maybeNextMaxId, maybeMinIdStorage) match {
        case (Some(nextMaxId), Some(_)) =>
          Some(params.copy(queryParameters = params.queryParameters + ("max_id" -> nextMaxId)))
        case (Some(nextMaxId), None) =>
          Some(params.copy(
            queryParameters = params.queryParameters + ("max_id" -> nextMaxId),
            storageParameters = Some(params.storage + ("min_id" -> extractHeadId(content)))))
        case (None, _) => None
      }
    }
  }

  def buildNextSync(content: JsValue, params: ApiEndpointCall): ApiEndpointCall = {
    logger.debug(s"Building next sync...")

    params.storage.get("min_id").map { savedMinId =>
      params.copy(
        queryParameters = params.queryParameters + ("min_id" -> savedMinId) - "max_id",
        storageParameters = Some(params.storage - "min_id"))
    }.getOrElse {
      params.copy(queryParameters = params.queryParameters + ("min_id" -> extractHeadId(content)))
    }
  }

  override protected def processResults(
    content: JsValue,
    hatAddress: String,
    hatClientActor: ActorRef,
    fetchParameters: ApiEndpointCall)(implicit ec: ExecutionContext, timeout: Timeout): Future[Done] = {

    for {
      validatedData <- FutureTransformations.transform(validateMinDataStructure(content))
      processedData <- FutureTransformations.transform(tranformData(validatedData, fetchParameters))
      _ <- uploadHatData(namespace, endpoint, processedData, hatAddress, hatClientActor) // Upload the data
    } yield {
      logger.debug(s"Successfully synced new records for HAT $hatAddress")
      Done
    }
  }

  def validateMinDataStructure(rawData: JsValue): Try[JsArray] = {
    (rawData \ "data").toOption.map {
      case data: JsArray if data.validate[List[InstagramMedia]].isSuccess =>
        logger.info(s"Validated JSON array of ${data.value.length} items.")
        Success(data)
      case data: JsObject =>
        logger.error(s"Error validating data, some of the required fields missing:\n${data.toString}")
        Failure(SourceDataProcessingException(s"Error validating data, some of the required fields missing."))
      case data =>
        logger.error(s"Error parsing JSON object: ${data.validate[List[InstagramMedia]]}")
        Failure(SourceDataProcessingException(s"Error parsing JSON object."))
    }.getOrElse {
      logger.error(s"Error parsing JSON object, necessary property not found: ${rawData.toString}")
      Failure(SourceDataProcessingException(s"Error parsing JSON object, necessary property not found."))
    }
  }

  private def tranformData(value: JsArray, params: ApiEndpointCall): Try[JsArray] =
    if (params.queryParameters.get("min_id").isDefined) {
      Try(Json.toJson(value.as[List[InstagramMedia]].dropRight(1)).as[JsArray]) // Removes the last element of the array
    }
    else {
      Success(value)
    }

  private def extractHeadId(value: JsValue): String = (value \ "data" \ 0 \ "id").as[String]

  override def attachAccessToken(params: ApiEndpointCall, authInfo: OAuth2Info): ApiEndpointCall =
    params.copy(queryParameters = params.queryParameters + ("access_token" -> authInfo.accessToken))
}

object InstagramFeedInterface {
  val defaultApiEndpoint = ApiEndpointCall(
    "https://api.instagram.com/v1",
    "/users/self/media/recent",
    ApiEndpointMethod.Get("Get"),
    Map(),
    Map("count" -> "2"),
    Map(),
    Some(Map()))
}