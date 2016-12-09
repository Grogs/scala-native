package scala.scalanative
package optimizer

import java.io.File
import java.nio.file.Paths
import scalanative.linker.World
import scalanative.nir.Defn
import scalanative.nir.serialization.serializeText
import scalanative.io.{withScratchBuffer, VirtualDirectory}

trait Reporter {

  /** Gets called whenever optimizations starts. */
  def onStart(node: World.Node): Unit = ()

  /** Gets called right after pass transforms the method. */
  def onPass(node: World.Node, pass: Pass): Unit = ()

  /** Gets called with final result of optimization. */
  def onComplete(node: World.Node): Unit = ()
}

object Reporter {

  /** Default no-op reporter. */
  val empty: Reporter = new Reporter {}

  /** Dump textual NIR after every pass to given directory. */
  def toDirectory(file: File): Reporter = empty /*new Reporter {
    private var last: Int             = _
    private var dir: VirtualDirectory = _

    private def debug(assembly: Seq[Defn], suffix: String) =
      withScratchBuffer { buffer =>
        serializeText(assembly, buffer)
        buffer.flip
        dir.write(Paths.get(s"out.$suffix.hnir"), buffer)
      }

    override def onStart(assembly: Seq[Defn]): Unit = {
      last = 0
      dir = VirtualDirectory.local(file)
      debug(assembly, "00")
    }

    override def onPass(pass: Pass, assembly: Seq[nir.Defn]): Unit = {
      last += 1
      val padded = if (last < 10) "0" + last else "" + last
      debug(assembly, padded + "-" + pass.getClass.getSimpleName)
    }
  }*/
}
