package se.nimsa.sbx.box

import akka.testkit.TestKit
import org.scalatest.BeforeAndAfterEach
import akka.testkit.ImplicitSender
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import java.nio.file.Files
import scala.slick.driver.H2Driver
import se.nimsa.sbx.app.DbProps
import scala.slick.jdbc.JdbcBackend.Database
import akka.actor.Props
import se.nimsa.sbx.util.TestUtil
import se.nimsa.sbx.box.BoxProtocol._
import se.nimsa.sbx.dicom.DicomMetaDataDAO
import se.nimsa.sbx.dicom.DicomDispatchActor
import se.nimsa.sbx.dicom.DicomProtocol._
import se.nimsa.sbx.dicom.DicomPropertyValue._
import se.nimsa.sbx.dicom.DicomHierarchy._

class BoxServiceActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  def this() = this(ActorSystem("BoxServiceActorTestSystem"))

  val db = Database.forURL("jdbc:h2:mem:boxserviceactortest;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
  val dbProps = DbProps(db, H2Driver)

  val storage = Files.createTempDirectory("slicebox-test-storage-")

  val boxDao = new BoxDAO(H2Driver)
  val metaDataDao = new DicomMetaDataDAO(H2Driver)

  db.withSession { implicit session =>
    boxDao.create
    metaDataDao.create
  }

  val dicomService = system.actorOf(DicomDispatchActor.props(storage, dbProps), name = "DicomDispatch")

  val boxServiceActorRef = system.actorOf(Props(new BoxServiceActor(dbProps, storage, "http://testhost:1234")), name = "BoxService")

  override def beforeEach() {
    db.withSession { implicit session =>
      boxDao.listBoxes.foreach(box =>
        boxDao.removeBox(box.id))

      boxDao.listOutboxEntries.foreach(outboxEntry =>
        boxDao.removeOutboxEntry(outboxEntry.id))

      boxDao.listInboxEntries.foreach(inboxEntry =>
        boxDao.removeInboxEntry(inboxEntry.id))

      boxDao.listTransactionTagValues.foreach(tagValue =>
        boxDao.removeTransactionTagValue(tagValue.id))
    }
  }

  override def afterAll {
    TestKit.shutdownActorSystem(system)
    TestUtil.deleteFolder(storage)
  }

  "A BoxServiceActor" should {

    "create inbox entry for first file in transaction" in {
      db.withSession { implicit session =>

        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", "abc", "https://someurl.com", BoxSendMethod.POLL, false))

        boxServiceActorRef ! UpdateInbox(remoteBox.token, 123, 1, 2)

        expectMsg(InboxUpdated(remoteBox.token, 123, 1, 2))

        val inboxEntries = boxDao.listInboxEntries

        inboxEntries.size should be(1)
        inboxEntries.foreach(inboxEntry => {
          inboxEntry.remoteBoxId should be(remoteBox.id)
          inboxEntry.transactionId should be(123)
          inboxEntry.receivedImageCount should be(1)
          inboxEntry.totalImageCount should be(2)

        })
      }
    }

    "inbox entry is updated for next file in transaction" in {
      db.withSession { implicit session =>

        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", "abc", "https://someurl.com", BoxSendMethod.POLL, false))

        boxServiceActorRef ! UpdateInbox(remoteBox.token, 123, 1, 3)
        expectMsg(InboxUpdated(remoteBox.token, 123, 1, 3))

        boxServiceActorRef ! UpdateInbox(remoteBox.token, 123, 2, 3)
        expectMsg(InboxUpdated(remoteBox.token, 123, 2, 3))

        val inboxEntries = boxDao.listInboxEntries

        inboxEntries.size should be(1)
        inboxEntries.foreach(inboxEntry => {
          inboxEntry.remoteBoxId should be(remoteBox.id)
          inboxEntry.transactionId should be(123)
          inboxEntry.receivedImageCount should be(2)
          inboxEntry.totalImageCount should be(3)

        })
      }
    }

    "return OuboxEmpty for poll message when outbox is empty" in {
      db.withSession { implicit session =>
        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", "abc", "https://someurl.com", BoxSendMethod.POLL, false))

        boxServiceActorRef ! PollOutbox(remoteBox.token)

        expectMsg(OutboxEmpty)
      }
    }

    "return first outbox entry when receiving poll message" in {
      db.withSession { implicit session =>
        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", "abc", "https://someurl.com", BoxSendMethod.POLL, false))
        boxDao.insertOutboxEntry(OutboxEntry(-1, remoteBox.id, 987, 1, 2, 123, false))

        boxServiceActorRef ! PollOutbox(remoteBox.token)

        expectMsgPF() {
          case OutboxEntry(id, remoteBoxId, transactionId, sequenceNumber, totalImageCount, imageId, failed) =>
            remoteBoxId should be(remoteBox.id)
            transactionId should be(987)
            sequenceNumber should be(1)
            totalImageCount should be(2)
            imageId should be(123)
        }
      }
    }

    "remove all box tag values when all outbox entries for a transaction have been removed" in {
      db.withSession { implicit session =>
        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", "abc", "https://someurl.com", BoxSendMethod.POLL, false))

        val p1 = metaDataDao.insert(Patient(-1, PatientName("p1"), PatientID("s1"), PatientBirthDate("2000-01-01"), PatientSex("M")))
        val s1 = metaDataDao.insert(Study(-1, p1.id, StudyInstanceUID("stuid1"), StudyDescription("stdesc1"), StudyDate("19990101"), StudyID("stid1"), AccessionNumber("acc1"), PatientAge("12Y")))
        val e1 = metaDataDao.insert(Equipment(-1, Manufacturer("manu1"), StationName("station1")))
        val f1 = metaDataDao.insert(FrameOfReference(-1, FrameOfReferenceUID("frid1")))
        val r1 = metaDataDao.insert(Series(-1, s1.id, e1.id, f1.id, SeriesInstanceUID("seuid1"), SeriesDescription("sedesc1"), SeriesDate("19990101"), Modality("NM"), ProtocolName("prot1"), BodyPartExamined("bodypart1")))
        val i1 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.1"), ImageType("t1"), InstanceNumber("1")))
        val i2 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.2"), ImageType("t1"), InstanceNumber("1")))
        val i3 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.3"), ImageType("t1"), InstanceNumber("1")))
        val if1 = metaDataDao.insert(ImageFile(i1.id, FileName("file1")))
        val if2 = metaDataDao.insert(ImageFile(i2.id, FileName("file2")))
        val if3 = metaDataDao.insert(ImageFile(i3.id, FileName("file3")))

        val tagValues = Seq(
          BoxSendTagValue(r1.id, 0x00101010, "B"),
          BoxSendTagValue(r1.id, 0x00101012, "D"),
          BoxSendTagValue(r1.id, 0x00101014, "F"))

        val seriesIds = Seq(r1.id)

        boxServiceActorRef ! SendSeriesToRemoteBox(remoteBox.id, seriesIds, tagValues)

        expectMsgPF() {
          case ImagesSent(remoteBoxId, imageFileIds) =>
            remoteBoxId should be(remoteBox.id)
            imageFileIds should be(Seq(if1.id, if2.id, if3.id))
        }

        val outboxEntries = boxDao.listOutboxEntries
        outboxEntries.size should be(3)

        val transactionId = outboxEntries(0).transactionId
        outboxEntries.map(_.transactionId).forall(_ == transactionId) should be(true)

        boxDao.listTransactionTagValues.size should be(3 * 3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if1.id, transactionId).size should be(3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if2.id, transactionId).size should be(3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if3.id, transactionId).size should be(3)

        outboxEntries.map(_.id).foreach(id => boxServiceActorRef ! RemoveOutboxEntry(id))

        expectMsgType[OutboxEntryRemoved]
        expectMsgType[OutboxEntryRemoved]
        expectMsgType[OutboxEntryRemoved]

        boxDao.listTransactionTagValues.isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if1.id, transactionId).isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if2.id, transactionId).isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if3.id, transactionId).isEmpty should be(true)
      }
    }

    "remove all box tag values when last outbox entry has been processed" in {
      db.withSession { implicit session =>
        val token = "abc"
        val remoteBox = boxDao.insertBox(Box(-1, "some remote box", token, "https://someurl.com", BoxSendMethod.POLL, false))

        val p1 = metaDataDao.insert(Patient(-1, PatientName("p1"), PatientID("s1"), PatientBirthDate("2000-01-01"), PatientSex("M")))
        val s1 = metaDataDao.insert(Study(-1, p1.id, StudyInstanceUID("stuid1"), StudyDescription("stdesc1"), StudyDate("19990101"), StudyID("stid1"), AccessionNumber("acc1"), PatientAge("12Y")))
        val e1 = metaDataDao.insert(Equipment(-1, Manufacturer("manu1"), StationName("station1")))
        val f1 = metaDataDao.insert(FrameOfReference(-1, FrameOfReferenceUID("frid1")))
        val r1 = metaDataDao.insert(Series(-1, s1.id, e1.id, f1.id, SeriesInstanceUID("seuid1"), SeriesDescription("sedesc1"), SeriesDate("19990101"), Modality("NM"), ProtocolName("prot1"), BodyPartExamined("bodypart1")))
        val i1 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.1"), ImageType("t1"), InstanceNumber("1")))
        val i2 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.2"), ImageType("t1"), InstanceNumber("1")))
        val i3 = metaDataDao.insert(Image(-1, r1.id, SOPInstanceUID("1.3"), ImageType("t1"), InstanceNumber("1")))
        val if1 = metaDataDao.insert(ImageFile(i1.id, FileName("file1")))
        val if2 = metaDataDao.insert(ImageFile(i2.id, FileName("file2")))
        val if3 = metaDataDao.insert(ImageFile(i3.id, FileName("file3")))

        val tagValues = Seq(
          BoxSendTagValue(r1.id, 0x00101010, "B"),
          BoxSendTagValue(r1.id, 0x00101012, "D"),
          BoxSendTagValue(r1.id, 0x00101014, "F"))

        val seriesIds = Seq(r1.id)

        boxServiceActorRef ! SendSeriesToRemoteBox(remoteBox.id, seriesIds, tagValues)

        expectMsgPF() {
          case ImagesSent(remoteBoxId, imageFileIds) =>
            remoteBoxId should be(remoteBox.id)
            imageFileIds should be(Seq(if1.id, if2.id, if3.id))
        }

        val outboxEntries = boxDao.listOutboxEntries
        outboxEntries.size should be(3)

        val transactionId = outboxEntries(0).transactionId
        outboxEntries.map(_.transactionId).forall(_ == transactionId) should be(true)

        boxDao.listTransactionTagValues.size should be(3 * 3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if1.id, transactionId).size should be(3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if2.id, transactionId).size should be(3)
        boxDao.tagValuesByImageFileIdAndTransactionId(if3.id, transactionId).size should be(3)

        outboxEntries.foreach(entry => boxServiceActorRef ! DeleteOutboxEntry(token, entry.transactionId, entry.sequenceNumber))

        expectMsg(OutboxEntryDeleted)
        expectMsg(OutboxEntryDeleted)
        expectMsg(OutboxEntryDeleted)

        boxDao.listTransactionTagValues.isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if1.id, transactionId).isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if2.id, transactionId).isEmpty should be(true)
        boxDao.tagValuesByImageFileIdAndTransactionId(if3.id, transactionId).isEmpty should be(true)
      }
    }

  }
}