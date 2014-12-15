package se.vgregion.dicom

import spray.json.DefaultJsonProtocol

object DicomHierarchy {
	import DicomPropertyValue._

  case class Patient(
    patientName: PatientName,
    patientID: PatientID,
    patientBirthDate: PatientBirthDate,
    patientSex: PatientSex) {
    override def equals(o: Any): Boolean = o match {
      case that: Patient => that.patientName == patientName && that.patientID == patientID
      case _ => false
    }
  }

  case class Study(
    patient: Patient,
    studyInstanceUID: StudyInstanceUID,
    studyDescription: StudyDescription,
    studyDate: StudyDate,
    studyID: StudyID,
    accessionNumber: AccessionNumber) {
    override def equals(o: Any): Boolean = o match {
      case that: Study => that.patient == patient && that.studyInstanceUID == studyInstanceUID
      case _ => false
    }
  }

  case class Equipment(
    manufacturer: Manufacturer,
    stationName: StationName)

  case class FrameOfReference(
    frameOfReferenceUID: FrameOfReferenceUID)

  case class Series(
    study: Study,
    equipment: Equipment,
    frameOfReference: FrameOfReference,
    seriesInstanceUID: SeriesInstanceUID,
    seriesDescription: SeriesDescription,
    seriesDate: SeriesDate,
    modality: Modality,
    protocolName: ProtocolName,
    bodyPartExamined: BodyPartExamined) {
    override def equals(o: Any): Boolean = o match {
      case that: Series => that.study == study && that.seriesInstanceUID == seriesInstanceUID
      case _ => false
    }
  }

  case class Image(
    series: Series,
    sopInstanceUID: SOPInstanceUID,
    imageType: ImageType) {
    override def equals(o: Any): Boolean = o match {
      case that: Image => that.series == series && that.sopInstanceUID == sopInstanceUID
      case _ => false
    }
  }

  object Equipment extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat2(Equipment.apply) }
  object FrameOfReference extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat1(FrameOfReference.apply) }
  object Patient extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat4(Patient.apply) }
  object Study extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat6(Study.apply) }
  object Series extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat9(Series.apply) }
  object Image extends DefaultJsonProtocol { implicit val format = DefaultJsonProtocol.jsonFormat3(Image.apply) }

}