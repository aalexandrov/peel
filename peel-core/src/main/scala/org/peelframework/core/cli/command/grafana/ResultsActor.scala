package org.peelframework.core.cli.command.grafana

import java.sql.Connection

import akka.actor.{Actor, ActorLogging, Props}
import anorm.SqlParser.{get => fetch}
import anorm._
import org.springframework.context.ApplicationContext
import spray.http.MediaTypes._
import spray.httpx.SprayJsonSupport
import spray.httpx.SprayJsonSupport._
import spray.httpx.marshalling._
import spray.httpx.unmarshalling._
import spray.json.DefaultJsonProtocol._
import spray.json._
import spray.routing._

import Results._
import Results.SearchRequestJsonSupport.sprayJsonUnmarshaller

import scala.language.postfixOps

protected class ResultsActor(val appContext: ApplicationContext, val conn: Connection)
  extends HttpServiceActor with Results {

  def receive = runRoute(routes)
}

protected object ResultsActor {

  /** Props constructor. */
  def props(context: ApplicationContext, conn: Connection): Props = {
    Props(new ResultsActor(context, conn: Connection))
  }
}

protected trait Results extends HttpService with ActorLogging {
  actor: Actor =>

  val appContext: ApplicationContext

  implicit val conn: Connection

  log.info("Search series")
  val ser = series()
  log.info("Render series")

  def routes: Route = {
    path("") {
      respondWithMediaType(`text/html`) {
        complete {
          <html>
            <body>
              <h1>Grafana Peel Source</h1>
            </body>
          </html>
        }
      }
    }
  } ~ {
    path("search") {
      respondWithMediaType(`application/json`) {
        complete {
          val res = marshal(ser)
          log.info("Done")
          res
        }
      }
    }
  } ~ {
    path("query") {
      respondWithMediaType(`application/json`) {
        ctx => ctx.complete {
          val entity = ctx.request.entity
          val value = entity.as[SearchRequest]
          log.info(entity.toString)
          """
            |[
            |  {
            |    "target":"upper_75",
            |    "datapoints":[
            |      [622,1450754160000],
            |      [365,1450754220000]
            |    ]
            |  },
            |  {
            |    "target":"upper_90",
            |    "datapoints":[
            |      [861,1450754160000],
            |      [767,1450754220000]
            |    ]
            |  }
            |]
          """.stripMargin.trim
        }
      }
    }
  }

  def series()(implicit conn: Connection): List[String] = {
    SQL(
      """
        |SELECT DISTINCT
        |       ex.suite          AS exp_suite,
        |       ex.name           AS exp_name,
        |       ee.host           AS exp_host,
        |       ee.name           AS seq_name
        |FROM   experiment        AS ex,
        |       experiment_run    AS er,
        |       experiment_event  AS ee
        |WHERE  ex.id = er.experiment_id
        |AND    er.id = ee.experiment_run_id
        |AND    ee.name LIKE 'dstat%'
        |AND    er.id IN (SELECT run_id FROM median_runs)
        |AND    er.id IN (SELECT run_id FROM median_runs)
        |LIMIT  50;
      """.stripMargin.trim
    ).as({
      {
        fetch[String]("exp_suite")
      } ~ {
        fetch[String]("exp_name")
      } ~ {
        fetch[String]("exp_host")
      } ~ {
        fetch[String]("seq_name")
      } map {
        case exp_suite ~ exp_name ~ exp_host ~ seq_name =>
          s"$exp_suite.$exp_name.$exp_host.$seq_name"
      }
    } *)
  }
}

object Results {

  //@formatter:off
  case class SearchRequest(
    panelId:       Int,
    range:         (String, String),
    rangeRaw:      (String, String),
    interval:      String,
    targets:       List[(String, String)],
    format:        String,
    maxDataPoints: Int
  )
  //@formatter:on

  case class Range(from: String, to: String)

  case class Target(target: String, refId: String)

  object SearchRequestJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val PortofolioFormats = jsonFormat7(SearchRequest)
  }

  object RangeJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val RangeJsonFormat = jsonFormat2(Range)
  }

  object TargetJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
    implicit val TargetFormat = jsonFormat2(Target)
  }
}