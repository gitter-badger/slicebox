/*
 * Copyright 2015 Karl Sjöstrand
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

package se.nimsa.sbx.box

import se.nimsa.sbx.model.Entity
import org.dcm4che3.data.Attributes

object BoxProtocol {

  sealed trait BoxSendMethod {
    override def toString(): String = this match {
      case BoxSendMethod.PUSH => "PUSH"
      case BoxSendMethod.POLL => "POLL"
    }
  }

  object BoxSendMethod {
    case object PUSH extends BoxSendMethod
    case object POLL extends BoxSendMethod

    def withName(string: String) = string match {
      case "PUSH" => PUSH
      case "POLL" => POLL
    }
  }

  case class RemoteBox(name: String, baseUrl: String)

  case class RemoteBoxName(value: String)

  case class BoxBaseUrl(value: String)

  case class Box(id: Long, name: String, token: String, baseUrl: String, sendMethod: BoxSendMethod, online: Boolean) extends Entity

  case class OutboxEntry(id: Long, remoteBoxId: Long, transactionId: Long, sequenceNumber: Long, totalImageCount: Long, imageFileId: Long, failed: Boolean) extends Entity

  case class OutboxEntryInfo(id: Long, remoteBoxName: String, transactionId: Long, sequenceNumber: Long, totalImageCount: Long, imageFileId: Long, failed: Boolean)

  case class InboxEntry(id: Long, remoteBoxId: Long, transactionId: Long, receivedImageCount: Long, totalImageCount: Long) extends Entity

  case class BoxSendTagValue(entityId: Long, tag: Int, value: String)

  case class TransactionTagValue(id: Long, imageFileId: Long, transactionId: Long, tag: Int, value: String) extends Entity

  case class AnonymizationKey(
    id: Long,
    created: Long,
    remoteBoxId: Long,
    transactionId: Long,
    remoteBoxName: String,
    patientName: String,
    anonPatientName: String,
    patientID: String,
    anonPatientID: String,
    patientBirthDate: String,
    studyInstanceUID: String,
    anonStudyInstanceUID: String,
    studyDescription: String,
    studyID: String,
    accessionNumber: String,
    seriesInstanceUID: String,
    anonSeriesInstanceUID: String,
    frameOfReferenceUID: String,
    anonFrameOfReferenceUID: String) extends Entity

  case class ReverseAnonymization(dataset: Attributes) extends BoxRequest

  case class HarmonizeAnonymization(outboxEntry: OutboxEntry, dataset: Attributes, anonDataset: Attributes) extends BoxRequest

  case class BoxSendData(entityIds: Seq[Long], tagValues: Seq[BoxSendTagValue])

  case class InboxEntryInfo(remoteBoxName: String, transactionId: Long, receivedImageCount: Long, totalImageCount: Long)

  case class PushImageData(transactionId: Long, sequenceNumber: Long, totalImageCount: Long, dataset: Attributes)

  sealed trait BoxRequest

  case class GenerateBoxBaseUrl(remoteBoxName: String) extends BoxRequest

  case class AddRemoteBox(remoteBox: RemoteBox) extends BoxRequest

  case class RemoveBox(boxId: Long) extends BoxRequest

  case object GetBoxes extends BoxRequest

  case class ValidateToken(token: String) extends BoxRequest

  case class UpdateInbox(token: String, transactionId: Long, sequenceNumber: Long, totalImageCount: Long) extends BoxRequest

  case class PollOutbox(token: String) extends BoxRequest

  case class SendPatientsToRemoteBox(remoteBoxId: Long, patientIds: Seq[Long], tagValues: Seq[BoxSendTagValue]) extends BoxRequest

  case class SendStudiesToRemoteBox(remoteBoxId: Long, studyIds: Seq[Long], tagValues: Seq[BoxSendTagValue]) extends BoxRequest

  case class SendSeriesToRemoteBox(remoteBoxId: Long, seriesIds: Seq[Long], tagValues: Seq[BoxSendTagValue]) extends BoxRequest

  case class GetOutboxEntry(token: String, transactionId: Long, sequenceNumber: Long) extends BoxRequest

  case class GetTransactionTagValues(imageFileId: Long, transactionId: Long) extends BoxRequest

  case class DeleteOutboxEntry(token: String, transactionId: Long, sequenceNumber: Long) extends BoxRequest

  case object GetInbox extends BoxRequest

  case object GetOutbox extends BoxRequest

  case class RemoveOutboxEntry(outboxEntryId: Long) extends BoxRequest

  case class GetAnonymizationKeys(startIndex: Long, count: Long, orderBy: Option[String], orderAscending: Boolean, filter: Option[String]) extends BoxRequest

  case class RemoveAnonymizationKey(anonymizationKeyId: Long) extends BoxRequest

  case class AnonymizationKeyRemoved(anonymizationKeyId: Long)

  case class AnonymizationKeys(anonymizationKeys: Seq[AnonymizationKey])

  case class OutboxEntryRemoved(outboxEntryId: Long)

  case class RemoteBoxAdded(box: Box)

  case class BoxRemoved(boxId: Long)

  case class Boxes(boxes: Seq[Box])

  case class BoxBaseUrlGenerated(baseUrl: String)

  case class ValidToken(token: String)

  case class InvalidToken(token: String)

  case class InboxUpdated(token: String, transactionId: Long, sequenceNumber: Long, totalImageCount: Long)

  case object OutboxEmpty

  case class ImagesSent(remoteBoxId: Long, imageFileIds: Seq[Long])

  case object OutboxEntryNotFound

  case object OutboxEntryDeleted

  case class Inbox(entries: Seq[InboxEntryInfo])

  case class Outbox(entries: Seq[OutboxEntryInfo])

  case object BoxNotFound

  // box push actor internal messages

  case object PollOutbox

  case class FileSent(outboxEntry: OutboxEntry)

  case class FileSendFailed(outboxEntry: OutboxEntry, statusCode: Int, e: Exception)

  // box poll actor internal messages

  case object PollRemoteBox

  case object RemoteOutboxEmpty

  case class RemoteOutboxEntryFound(remoteOutboxEntry: OutboxEntry)

  case class PollRemoteBoxFailed(e: Throwable)

  case class RemoteOutboxFileFetched(remoteOutboxEntry: OutboxEntry)

  case class FetchFileFailed(e: Throwable)

}
