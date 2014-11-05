package wetalk
/**
 * Created by goldratio on 11/2/14.
 */
case class ErrorInfo(summary: String = "", detail: String = "") {
  def withSummary(newSummary: String) = copy(summary = newSummary)
  def withSummaryPrepended(prefix: String) = withSummary(if (summary.isEmpty) prefix else prefix + ": " + summary)
  def withFallbackSummary(fallbackSummary: String) = if (summary.isEmpty) withSummary(fallbackSummary) else this
  def formatPretty = if (summary.isEmpty) detail else if (detail.isEmpty) summary else summary + ": " + detail
  def format(withDetail: Boolean): String = if (withDetail) formatPretty else summary
}

object ErrorInfo {
  def fromCompoundString(message: String): ErrorInfo = message.split(": ", 2) match {
    case Array(summary, detail) ⇒ apply(summary, detail)
    case _                      ⇒ ErrorInfo("", message)
  }
}

/** Marker for exceptions that provide an ErrorInfo */
abstract class ExceptionWithErrorInfo(val info: ErrorInfo) extends RuntimeException(info.formatPretty)

