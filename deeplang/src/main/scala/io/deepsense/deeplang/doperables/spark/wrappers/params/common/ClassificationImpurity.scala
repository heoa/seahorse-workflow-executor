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

package io.deepsense.deeplang.doperables.spark.wrappers.params.common

import io.deepsense.deeplang.doperables.spark.wrappers.params.common.ClassificationImpurity.{Gini, Entropy}
import io.deepsense.deeplang.params.Param
import io.deepsense.deeplang.params.choice.Choice

sealed abstract class ClassificationImpurity(override val name: String) extends Choice {

  override val params: Array[Param[_]] = Array()

  override val choiceOrder: List[Class[_ <: Choice]] = List(
    classOf[Entropy],
    classOf[Gini]
  )
}

object ClassificationImpurity {
  case class Entropy() extends ClassificationImpurity("entropy")
  case class Gini() extends ClassificationImpurity("gini")
}
