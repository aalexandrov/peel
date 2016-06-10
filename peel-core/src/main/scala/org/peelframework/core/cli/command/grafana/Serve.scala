package org.peelframework.core.cli.command.grafana

import java.lang.{System => Sys}

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.peelframework.core.cli.command.Command
import org.peelframework.core.config.loadConfig
import org.peelframework.core.results.DB
import org.peelframework.core.util.console._
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import spray.can.Http

import scala.concurrent.duration._

/** Initialize results database. */
@Service("grafana:serve")
class Serve extends Command {

  override val help = "serve results as Graphana source"

  override def register(parser: Subparser) = {
    // options
    parser.addArgument("--connection")
      .`type`(classOf[String])
      .dest("app.db.connection")
      .metavar("ID")
      .help("database config name (default: `default`)")

    // option defaults
    parser.setDefault("app.db.connection", "default")
  }

  override def configure(ns: Namespace) = {
    // set ns options and arguments to system properties
    Sys.setProperty("app.db.connection", ns.getString("app.db.connection"))
  }

  override def run(context: ApplicationContext) = {
    // load application configuration
    implicit val config = loadConfig()

    val host = config.getString(s"app.grafana.source.host")
    val port = config.getInt(s"app.grafana.source.port")

    // create database connection
    val connName = Sys.getProperty("app.db.connection")
    implicit val conn = DB.getConnection(connName)

    logger.info(s"Serving Graphana source at '$host:$port' for database '$connName'")

    try {

      implicit val system = ActorSystem("peel-grafana-server")

      val results = system.actorOf(ResultsActor.props(context, conn), "results-service")

      implicit val timeout = Timeout(10.seconds)

      // start a new HTTP with our REST results service
      IO(Http) ? Http.Bind(results, interface = host, port = port)
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error while serving Graphana source database '$connName': ${e.getMessage}".red)
        throw e
    }
  }
}
