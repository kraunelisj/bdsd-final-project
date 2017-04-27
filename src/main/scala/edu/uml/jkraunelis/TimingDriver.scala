package edu.uml.jkraunelis

import breeze.linalg.NotConvergedException
import org.mitre.mandolin.glp.GLPModelSettings
import org.mitre.mandolin.mselect._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.io.Source


object TimingDriver {
  def main(args: Array[String]): Unit = {
    // input file
    val filename = args(2)
    // create Mandolin settings object
    val appSettings = new GLPModelSettings(args.slice(0, 2)) with ModelSelectionSettings

    // create parameter maps for all 3 types of params
    val categoricalMPMap = new mutable.HashMap[String, CategoricalMetaParameter]
    val realMPMap = new mutable.HashMap[String, RealMetaParameter]
    val intMPMap = new mutable.HashMap[String, IntegerMetaParameter]
    appSettings.modelSpace.catMPs.map { mp => categoricalMPMap.put(mp.name, mp) }
    appSettings.modelSpace.realMPs.map { mp => realMPMap.put(mp.name, mp) }
    appSettings.modelSpace.intMPs.map { mp => intMPMap.put(mp.name, mp) }

    //TODO: figure out how to get these values
    //    intMPMap.put("totalWeights", new IntegerMetaParameter("totalWeights", new IntSet(5000, 500000)))
    //    intMPMap.put("numHiddenLayers", new IntegerMetaParameter("numHiddenLayers", new IntSet(1, 2)))

    intMPMap.put("mandolin.mx.specification.fc0.num_hidden", new IntegerMetaParameter("mandolin.mx.specification.fc0.num_hidden", new IntSet(64, 512)))
    intMPMap.put("mandolin.mx.specification.fc1.num_hidden", new IntegerMetaParameter("mandolin.mx.specification.fc1.num_hidden", new IntSet(32, 256)))
    intMPMap.put("mandolin.trainer.mini-batch-size", new IntegerMetaParameter("mandolin.trainer.mini-batch-size", new IntSet(64, 256)))
    intMPMap.put("mandolin.trainer.num-epochs", new IntegerMetaParameter("mandolin.trainer.num-epochs", new IntSet(10, 190)))


    var scoredModelConfigs = new ListBuffer[ScoredModelConfig]
    var trainingModelConfigs = new ListBuffer[ScoredModelConfig]

    for (line <- Source.fromFile(filename).getLines()) {
      val fields = line.split(" ")

      val catVMPs = new ListBuffer[ValuedMetaParameter[CategoricalValue]]()
      val realVMPs = new ListBuffer[ValuedMetaParameter[RealValue]]()
      val intVMPs = new ListBuffer[ValuedMetaParameter[IntValue]]()

      val accuracy = fields(0).split(":")(1).toDouble
      val time = fields(1).split(":")(1).toLong

      for (i <- 2 until fields.length) {
        val field = fields(i)
        if (!field.isEmpty()) {
          val nameValuePair = field.split(":")

          if (categoricalMPMap.contains(nameValuePair(0))) {
            catVMPs.append(new ValuedMetaParameter(new CategoricalValue(nameValuePair(1)), categoricalMPMap.get(nameValuePair(0)).get))
          } else if (realMPMap.contains(nameValuePair(0))) {
            realVMPs.append(new ValuedMetaParameter(new RealValue(nameValuePair(1).toDouble), realMPMap.get(nameValuePair(0)).get))
          } else if (intMPMap.contains(nameValuePair(0))) {
            intVMPs.append(new ValuedMetaParameter(new IntValue(nameValuePair(1).toInt), intMPMap.get(nameValuePair(0)).get))
          } else {
            println("failed to find MetaParameter " + nameValuePair(0))
          }
        }
      }

      val realValued = realVMPs.toVector
      val catValued = catVMPs.toVector
      val intValued = intVMPs.toVector

      val modelConfig = new ModelConfig(1, realValued, catValued, intValued, appSettings.modelSpace.inLType, appSettings.modelSpace.outLType, appSettings.modelSpace.idim, appSettings.modelSpace.odim,
        None)

      val scoredModelConfig = new ScoredModelConfig(accuracy, time, modelConfig)
      scoredModelConfigs.append(scoredModelConfig)
    }

    val modelSpace = new ModelSpace(realMPMap.values.toVector, categoricalMPMap.values.toVector, intMPMap.values.toVector)

    var bestAcc = -1E10
    var runtime = 0L

    println("best_accuracy,time")
    // initialize the training set
    for (i <- 0 to 10) {
      val config = scoredModelConfigs.remove(0)
      trainingModelConfigs.append(config)
      runtime += config.t
      if (bestAcc < config.sc) {
        bestAcc = config.sc
        println(bestAcc + "," + runtime)
      }
    }


    // training/evaluation loop
    for (i <- 1 to 310) {
      val accuracyScoringFunction = new BayesianNNScoringFunction(modelSpace, new UpperConfidenceBound(0.3), 1000)
      val timingScoringFunction = new BayesianNNScoringFunctionWTiming(modelSpace, new UpperConfidenceBound(0), 1000)
      try {
        accuracyScoringFunction.train(trainingModelConfigs)
        timingScoringFunction.train(trainingModelConfigs)
        val results = scoredModelConfigs.map { sc =>
          val pred_acc = accuracyScoringFunction.score(sc.mc)
          val real_acc = sc.sc
          val pred_time = timingScoringFunction.score(sc.mc)
          val real_time = sc.t
          //println(s"pred_acc=$pred_acc real_acc=$real_acc pred_time=$pred_time real_time=$real_time")
          val score = (pred_acc - args(3).toFloat * pred_time)
          (score, sc)
        }
        // pick the top10 best configs
        val picks = results.sortBy {
          _._1
        }.reverse take (10)
        picks.foreach { pick =>
          // add this pick to the training set
          trainingModelConfigs.append(pick._2)
          // increase the runtime incurred by evaluating this pick
          runtime += pick._2.t
          // if the real eval accuracy is better than the current best, increase the current best
          if (bestAcc < pick._2.sc) {
            bestAcc = pick._2.sc
            println(bestAcc + "," + runtime)
          }
        }
        // remove the picks that are now in the training set from the scored configs
        println("scoredModelConfigs.size=" + scoredModelConfigs.size)
        scoredModelConfigs --= picks.map {
          _._2
        }

      } catch {
        case e: NotConvergedException => {
          println("Training error, skipping iteration")

        }
      }
    }
  }
}