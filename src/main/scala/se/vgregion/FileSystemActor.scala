package se.vgregion

import akka.actor.Actor
import akka.event.{LoggingReceive, Logging}

class FileSystemActor extends Actor {
	val log = Logging(context.system, this)
	val watchServiceTask = new WatchServiceTask(self)
	val watchThread = new Thread(watchServiceTask, "WatchService")

	override def preStart() {
		watchThread.setDaemon(true)
		watchThread.start()
	}

	override def postStop() {
		watchThread.interrupt()
	}

	def receive = LoggingReceive {
		case MonitorDir(path) =>
			watchServiceTask watchRecursively path
		case Created(file) =>
		case Deleted(fileOrDir) =>
	}
}