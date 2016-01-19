/**
 * Copyright 2015, deepsense.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.deepsense.deeplang.doperations

import scala.reflect.runtime.universe.TypeTag

import org.apache.spark.ml
import org.apache.spark.ml.param.{Param, ParamMap}
import org.apache.spark.ml.tuning.{ParamGridBuilder, CrossValidator, CrossValidatorModel}
import spray.json.{JsNull, JsValue}

import io.deepsense.commons.types.ColumnType
import io.deepsense.commons.utils.DoubleUtils
import io.deepsense.deeplang.DOperation.Id
import io.deepsense.deeplang.doperables._
import io.deepsense.deeplang.doperables.dataframe.DataFrame
import io.deepsense.deeplang.doperables.wrappers.{EstimatorWrapper, EvaluatorWrapper}
import io.deepsense.deeplang.params.wrappers.deeplang.ParamWrapper
import io.deepsense.deeplang.params.{DynamicParam, ParamPair, NumericParam}
import io.deepsense.deeplang.params.gridsearch.GridSearchParam
import io.deepsense.deeplang.params.validators.RangeValidator
import io.deepsense.deeplang.{DOperation3To1, ExecutionContext}
import io.deepsense.reportlib.model.{ReportContent, ReportType, Table}

case class GridSearch() extends DOperation3To1[DataFrame, Estimator, Evaluator, Report] {

  override val name: String = "Grid Search"
  override val id: Id = "9163f706-eaaf-46f6-a5b0-4114d92032b7"
  override val description: String = "Uses Cross-validation to find the best set of parameters " +
    "for input estimator. User can specify a list of parameter values to test and compare."

  val estimatorParams = new GridSearchParam(
    name = "Parameters of input Estimator",
    description = "These parameters are rendered dynamically, depending on type of Estimator.",
    inputPort = 1)
  setDefault(estimatorParams, JsNull)

  val evaluatorParams = new DynamicParam(
    name = "Parameters of input Evaluator",
    description = "These parameters are rendered dynamically, depending on type of Evaluator.",
    inputPort = 2)
  setDefault(evaluatorParams, JsNull)

  val numberOfFolds = new NumericParam(
    name = "number of folds",
    description = "Number of folds.",
    validator = RangeValidator(begin = 2.0, end = Int.MaxValue, beginIncluded = true))
  setDefault(numberOfFolds, 2.0)

  def getNumberOfFolds: Int = $(numberOfFolds).toInt
  def setNumberOfFolds(numOfFolds: Int): this.type = set(numberOfFolds, numOfFolds.toDouble)

  def getEstimatorParams: JsValue = $(estimatorParams)
  def setEstimatorParams(jsValue: JsValue): this.type = set(estimatorParams, jsValue)

  def getEvaluatorParams: JsValue = $(evaluatorParams)
  def setEvaluatorParams(jsValue: JsValue): this.type = set(evaluatorParams, jsValue)

  override val params = declareParams(estimatorParams, evaluatorParams, numberOfFolds)

  override lazy val tTagTI_0: TypeTag[DataFrame] = typeTag
  override lazy val tTagTI_1: TypeTag[Estimator] = typeTag
  override lazy val tTagTI_2: TypeTag[Evaluator] = typeTag
  override lazy val tTagTO_0: TypeTag[Report] = typeTag

  override protected def _execute(
      context: ExecutionContext)(
      dataFrame: DataFrame,
      estimator: Estimator,
      evaluator: Evaluator): Report = {

    val estimatorParams = estimator.paramPairsFromJson(getEstimatorParams)
    val estimatorWithParams = createEstimatorWithParams(estimator, estimatorParams)
    val evaluatorWithParams = createEvaluatorWithParams(evaluator)
    val estimatorWrapper: EstimatorWrapper = new EstimatorWrapper(context, estimatorWithParams)
    val gridSearchParams: Array[ParamMap] =
      createGridSearchParams(estimatorWrapper.uid, estimatorParams)
    val cv = new CrossValidator()
      .setEstimator(estimatorWrapper)
      .setEvaluator(new EvaluatorWrapper(context, evaluatorWithParams))
      .setEstimatorParamMaps(gridSearchParams)
      .setNumFolds(getNumberOfFolds)
    val cvModel: CrossValidatorModel = cv.fit(dataFrame.sparkDataFrame)
    createReport(gridSearchParams, cvModel.avgMetrics, evaluator.isLargerBetter)
  }

  private def createReport(
      gridSearchParams: Array[ParamMap],
      metrics: Array[Double],
      isLargerMetricBetter: Boolean): Report = {
    val paramsWithOrder: Seq[Param[_]] = gridSearchParams.head.toSeq.map(_.param).sortBy(_.name)
    val bestMetric: Metric = findBestMetric(metrics, isLargerMetricBetter)
    Report(ReportContent("Grid Search", ReportType.GridSearch, tables = Seq(
      bestParamsMetricsTable(gridSearchParams, paramsWithOrder, bestMetric),
      cvParamsMetricsTable(gridSearchParams, paramsWithOrder, metrics)
    )))
  }

  private def bestParamsMetricsTable(
      gridSearchParams: Array[ParamMap],
      paramsWithOrder: Seq[Param[_]],
      bestMetric: Metric): Table = {
    Table(
      name = "Best Params",
      description = "Best Parameters Values",
      columnNames = metricsTableColumnNames(paramsWithOrder),
      columnTypes = metricsTableColumnTypes(paramsWithOrder),
      rowNames = None,
      values = List(
        metricsTableRow(
          gridSearchParams(bestMetric.metricIndex),
          paramsWithOrder,
          bestMetric.metricValue)
      )
    )
  }

  private def cvParamsMetricsTable(
      gridSearchParams: Array[ParamMap],
      paramsWithOrder: Seq[Param[_]],
      metrics: Array[Double]): Table = {
    Table(
      name = "Params",
      description = "Parameters Values",
      columnNames = metricsTableColumnNames(paramsWithOrder),
      columnTypes = metricsTableColumnTypes(paramsWithOrder),
      rowNames = None,
      values = gridSearchParams.zipWithIndex.map { case (cvParamMap, index) =>
        metricsTableRow(cvParamMap, paramsWithOrder, metrics(index))
      }.toList
    )
  }

  private def metricsTableRow(
      cvParamMap: ParamMap,
      paramsWithOrder: Seq[Param[_]],
      metric: Double): List[Option[String]] = {
    paramMapToTableRow(cvParamMap, paramsWithOrder) :+ Some(DoubleUtils.double2String(metric))
  }

  private def metricsTableColumnTypes(paramsWithOrder: Seq[Param[_]]): List[ColumnType.Value] = {
    (paramsWithOrder.map(_ => ColumnType.numeric) :+ ColumnType.numeric).toList
  }

  private def metricsTableColumnNames(paramsWithOrder: Seq[Param[_]]): Some[List[String]] = {
    Some((paramsWithOrder.map(_.name) :+ "Metric").toList)
  }

  private def findBestMetric(metrics: Array[Double], isLargerMetricBetter: Boolean): Metric = {
    val (bestMetric, bestIndex) = if (isLargerMetricBetter) {
      metrics.zipWithIndex.maxBy(_._1)
    } else {
      metrics.zipWithIndex.minBy(_._1)
    }
    Metric(bestMetric, bestIndex)
  }

  private def paramMapToTableRow(
      paramMap: ParamMap,
      orderedParams: Seq[ml.param.Param[_]]): List[Option[String]] = {
    orderedParams.map(paramMap.get(_).map(_.toString)).toList
  }

  private def createEstimatorWithParams(
      estimator: Estimator,
      estimatorParams: Seq[ParamPair[_]]): Estimator = {
    estimator.replicate().set(estimatorParams: _*)
  }

  private def createEvaluatorWithParams(evaluator: Evaluator): Evaluator = {
    evaluator.replicate().setParamsFromJson($(evaluatorParams))
  }

  private def createGridSearchParams(
      estimatorUID: String,
      params: Seq[ParamPair[_]]): Array[ml.param.ParamMap] = {

    params.filter(paramPair => paramPair.param.isGriddable).foldLeft(new ParamGridBuilder()) {
      case (builder, paramPair) =>
        val sparkParam = new ParamWrapper(estimatorUID, paramPair.param)
        builder.addGrid(sparkParam, paramPair.values)
    }.build()
  }

  private case class Metric(metricValue: Double, metricIndex: Int)
}
