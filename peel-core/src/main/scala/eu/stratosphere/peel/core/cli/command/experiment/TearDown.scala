package eu.stratosphere.peel.core.cli.command.experiment

import java.lang.{System => Sys}

import eu.stratosphere.peel.core.beans.experiment.ExperimentSuite
import eu.stratosphere.peel.core.beans.system.{Lifespan, System}
import eu.stratosphere.peel.core.cli.command.Command
import eu.stratosphere.peel.core.config.{Configurable, loadConfig}
import eu.stratosphere.peel.core.graph.createGraph
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/** Tear down systems for a specific experiment. */
@Service("exp:teardown")
class TearDown extends Command {

  override val name = "exp:teardown"

  override val help = "tear down systems for a specific experiment"

  override def register(parser: Subparser) = {
    // arguments
    parser.addArgument("suite")
      .`type`(classOf[String])
      .dest("app.suite.name")
      .metavar("SUITE")
      .help("suite containing the experiment")
    parser.addArgument("experiment")
      .`type`(classOf[String])
      .dest("app.suite.experiment.name")
      .metavar("EXPERIMENT")
      .help("experiment to run")
  }

  override def configure(ns: Namespace) = {
    // set ns options and arguments to system properties
    Sys.setProperty("app.suite.name", ns.getString("app.suite.name"))
    Sys.setProperty("app.suite.experiment.name", ns.getString("app.suite.experiment.name"))
  }

  override def run(context: ApplicationContext) = {
    val suiteName = Sys.getProperty("app.suite.suite.name")
    val expName = Sys.getProperty("app.suite.experiment.name")

    logger.info(s"Running experiment '${Sys.getProperty("app.suite.experiment.name")}' from suite '${Sys.getProperty("app.suite.name")}'")
    val suite = context.getBean(Sys.getProperty("app.suite.name"), classOf[ExperimentSuite])
    val graph = createGraph(suite)

    //TODO check for cycles in the graph
    if (graph.isEmpty) throw new RuntimeException("Experiment suite is empty!")

    // find experiment
    val expOption = suite.experiments.find(_.name == expName)
    // check if experiment exists (the list should contain exactly one element)
    if (expOption.isEmpty) throw new RuntimeException(s"Experiment '$expName' either not found or ambigous in suite '$suiteName'")

    for (exp <- expOption) {
      // update config
      exp.config = loadConfig(graph, exp)
      for (n <- graph.descendants(exp)) n match {
        case s: Configurable => s.config = exp.config
        case _ => Unit
      }

      logger.info("Tearing down systems for experiment '%s'".format(exp.name))
      for (n <- graph.traverse(); if graph.descendants(exp).contains(n)) n match {
        case s: System if Lifespan.SUITE :: Lifespan.EXPERIMENT :: Nil contains s.lifespan => s.tearDown()
        case _ => Unit
      }
    }

  }
}
