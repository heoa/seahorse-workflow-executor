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

package io.deepsense.workflowexecutor.communication

import spray.json._

import io.deepsense.commons.utils.Version
import io.deepsense.models.json.graph.GraphJsonProtocol.GraphReader

case class ProtocolDeserializer(
    override val graphReader: GraphReader,
    override val currentVersion: Version)
  extends MQMessageDeserializer
  with ConnectJsonProtocol
  with LaunchJsonProtocol
  with GetPythonGatewayAddressJsonProtocol {

  override protected def deserializeJson(jsObject: JsObject): ReadMessageMQ = {
    val fields = jsObject.fields
    val messageType = fields(MessageMQ.messageTypeKey).convertTo[String]
    val body = fields(MessageMQ.messageBodyKey)

    messageType match {
      case Connect.messageType => body.convertTo[Connect]
      case Launch.messageType => body.convertTo[Launch]
      case GetPythonGatewayAddress.messageType => body.convertTo[GetPythonGatewayAddress]
    }
  }
}