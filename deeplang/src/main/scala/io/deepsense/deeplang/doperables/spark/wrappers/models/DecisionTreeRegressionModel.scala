/**
 * Copyright 2016, deepsense.io
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

package io.deepsense.deeplang.doperables.spark.wrappers.models

import org.apache.spark.ml.regression.{DecisionTreeRegressionModel => SparkDecisionTreeRegressionModel, DecisionTreeRegressor => SparkDecisionTreeRegressor}

import io.deepsense.deeplang.doperables.spark.wrappers.params.common.{HasFeaturesColumnParam, HasPredictionColumnCreatorParam}
import io.deepsense.deeplang.doperables.{LoadableWithFallback, SparkModelWrapper}
import io.deepsense.deeplang.params.Param
import io.deepsense.sparkutils.ML

class DecisionTreeRegressionModel
  extends SparkModelWrapper[
    SparkDecisionTreeRegressionModel,
    SparkDecisionTreeRegressor]
  with HasFeaturesColumnParam
  with HasPredictionColumnCreatorParam
  with LoadableWithFallback[
    SparkDecisionTreeRegressionModel,
    SparkDecisionTreeRegressor] {

  override val params: Array[Param[_]] = Array(
    featuresColumn,
    predictionColumn)

  override def tryToLoadModel(path: String): Option[SparkDecisionTreeRegressionModel] = {
    ML.ModelLoading.decisionTreeRegression(path)
  }
}
