package dotty.dokka

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Test
import org.junit.Assert
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.nio.charset.Charset
import dotty.tools.dotc.core.Contexts._

class ReportingTest:
  import Scala3doc.Args

  private def checkReportedDiagnostics(
    newArgs: Args => Args = identity,
    ctx: Context = testContext)(
    op: ReportedDiagnostics => Unit): Unit =

    val dest = Files.createTempDirectory("test-doc")
    try
      val args = Args(
          name = "Test Project Name",
          output = dest.toFile,
          tastyFiles = tastyFiles("nested") // Random package
        )
      Scala3doc.run(newArgs(args))(using ctx)
      op(ctx.reportedDiagnostics)

    finally IO.delete(dest.toFile)

  @Test
  def noMessageForMostCases = checkReportedDiagnostics(){ diag =>
    assertNoWarning(diag)
    assertNoErrors(diag)
    assertNoInfos(diag)
  }

  @Test
  def noClassesErors = checkReportedDiagnostics(_.copy(tastyFiles = Nil)){ diag =>
    assertMessagesAbout(diag.errorMsgs)("classes should no be empty")
  }

  @Test
  def errorsInCaseOfIncompletClasspath =
    val notTasty = Files.createTempFile("broken", ".notTasty")
    try
      Files.write(notTasty, "Random file".getBytes)
      checkReportedDiagnostics(a => a.copy(tastyFiles = notTasty.toFile +: a.tastyFiles)){ diag =>
        assertMessagesAbout(diag.errorMsgs)("File extension is not `tasty` or `jar`")
      }
    finally Files.delete(notTasty)

  @Test
  def verbosePrintsDokkaMessage =
    val ctx = testContext
    ctx.setSetting(ctx.settings.verbose, true)
    checkReportedDiagnostics(ctx = ctx){ diag =>
      assertNoWarning(diag)
      assertNoErrors(diag)

      assertMessagesAbout(diag.infoMsgs)("dotty.dokka.DottyDokkaPlugin", "generation completed successfully")
    }