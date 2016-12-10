import scalanative.native._, stdlib._, stdio._

@extern
object dirent {

  @struct class DIR private ()
  @struct class dirent(val d_ino: Int, val d_name: CString)

  def opendir(filename: CString): Ptr[DIR] = extern
  def readdir(dir: Ptr[DIR]): Ptr[dirent]  = extern

}

object Test {
  def main(args: Array[String]): Unit = {

    val handle = dirent.opendir(c"/var/tmp")
    println(!handle)
    println("0")
    for (i <- 1 to 5) {
      println("1")
      val ent = dirent.readdir(handle)
      println("2")
      val deref = !ent
      println("3")
      val d_name = deref.d_name
      println("3.1")
      string.strlen(d_name).toInt
      println("4")
      val string1 = fromCString(d_name)
      println("File... " + string1)
    }
    println("5")

    println("Hello, world!")

  }
}
