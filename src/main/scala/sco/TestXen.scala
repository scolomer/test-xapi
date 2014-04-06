package sco

import grizzled.slf4j.Logger
import play.api.libs.ws._
import scala.concurrent.Future
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.{ future, promise }
import scala.concurrent.duration._
import scala.xml.NodeSeq
import com.twitter.util.Stopwatch
import com.twitter.util.Duration
import scala.concurrent.Await
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

case class StringSeq(seq: Seq[String])

class Settings(conf: Config) {
  val url = conf.getString("xenserver.url")
  val username = conf.getString("xenserver.username")
  val password = conf.getString("xenserver.password")
}

object TestXen extends App {
  val log = Logger[this.type]

  val settings = new Settings(ConfigFactory.load())
  
  def decodeValue(xml: NodeSeq): Any = {
    if ((xml \ "array") isEmpty) {
      xml.text
    } else {
      StringSeq((xml \ "array" \ "data" \ "value") map { _.text })
    }
  }

  def decodeResponse(xml: NodeSeq): Map[String, Any] = {
    (xml \ "params" \ "param" \ "value" \ "struct" \ "member")
      .foldLeft(Map[String, Any]()) { (a, b) =>
        a + ((b \ "name").text -> decodeValue(b \ "value"))
      }
  }

  def buildXmlRpcRequest(methodName: String, lst: Seq[String]) = {
    <methodCall>
      <methodName>{ methodName }</methodName>
      <params>
        {
          for (param <- lst) yield <param><value>{ param }</value></param>
        }
      </params>
    </methodCall>
  }

  def invokeStringMethod(methodName: String, params: Seq[String]): Future[String] = {
    WS.url(settings.url).post(buildXmlRpcRequest(methodName, params))
      .map { r =>
        val map = decodeResponse(r.xml);
        map("Status") match {
          case "Success" => map("Value") match {
            case value: String => value
            case _ => throw new Exception();
          }
          case _ => throw new Exception();
        }
      }
  }

  def invokeBooleanMethod(methodName: String, params: Seq[String]): Future[Boolean] = {
    WS.url(settings.url).post(buildXmlRpcRequest(methodName, params))
      .map { r =>
        val map = decodeResponse(r.xml);
        map("Status") match {
          case "Success" => map("Value") match {
            case value: String => value == "1"
            case _ => throw new Exception();
          }
          case _ => throw new Exception();
        }
      }
  }

  def invokeStringSeqMethod(methodName: String, params: Seq[String]): Future[Seq[String]] = {
    WS.url(settings.url).post(buildXmlRpcRequest(methodName, params))
      .map { r =>
        val map = decodeResponse(r.xml);
        map("Status") match {
          case "Success" => map("Value") match {
            case value: StringSeq => value.seq
            case _ => throw new Exception();
          }
          case _ => throw new Exception();
        }
      }
  }

  object UtilFuncs {
    def login(username: String, password: String) =
      invokeStringMethod("session.login_with_password", Seq(username, password, "2.0"))

    def getThisHost(sessionId: String) =
      invokeStringMethod("session.get_this_host", Seq(sessionId, sessionId))

    def getAllVM(sessionId: String) =
      invokeStringSeqMethod("VM.get_all", Seq(sessionId))

    def getAllRecordsVM(sessionId: String) =
      invokeStringSeqMethod("VM.get_all_records", Seq(sessionId))

    def getIsATemplateVM(sessionId: String, vmId: String) =
      invokeBooleanMethod("VM.get_is_a_template", Seq(sessionId, vmId))

    def getNameLabelVM(sessionId: String, vmId: String) =
      invokeStringMethod("VM.get_name_label", Seq(sessionId, vmId))

    def getNameDescriptionVM(sessionId: String, vmId: String) =
      invokeStringMethod("VM.get_name_description", Seq(sessionId, vmId))

  }

  // name_description

  class VM(val session: Session, val id: String, val nameLabel: String, val nameDescription: String);

  class Session(val id: String) {

    def loadVM(vmid: String): Future[VM] = {
      Future.sequence(Seq(UtilFuncs.getNameLabelVM(id, vmid), UtilFuncs.getNameDescriptionVM(id, vmid)))
        .map(a => new VM(this, vmid, a(0), a(1)))
    }

    def loadVms(): Future[Seq[VM]] = {
      UtilFuncs.getAllVM(id).flatMap(seq => Future.sequence(seq.map(vmid =>
        UtilFuncs.getIsATemplateVM(id, vmid).map(b => if (b) None else Some(vmid))))).map(_.filter(_.isDefined).map(_.get)).flatMap(a =>
        Future.sequence(a.map(vmid => loadVM(vmid))))
    }
  }

  object Session {
    def login(username: String, password: String): Future[Session] = {
      UtilFuncs.login(username, password).map(new Session(_));
    }
  }

  val elapsed = Stopwatch.start()

  val sessionFuture = Session.login(settings.username, settings.password)
  log.info(elapsed())
  val vmsFutures = sessionFuture.flatMap(_.loadVms())

  log.info(elapsed())

  vmsFutures onSuccess {
    case a =>
      log.info(elapsed())

      a.foreach { vm =>
        log.info(s"${vm.nameLabel} : ${vm.nameDescription}")
      }
  }

  vmsFutures onFailure {
    case a => log.error("Failed")
  }

}
