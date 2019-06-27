package com.cairn.common

import java.awt.Toolkit
import java.io.{File, FileFilter}

import javax.swing.UIManager
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.apache.log4j.{LogManager, Logger}

import scala.collection.JavaConverters._


/**
  * A trait to attach a logger to a class
  */
trait HasLogging {
  @transient
  protected lazy val logger: Logger = LogManager.getLogger(getClass)
}


trait LoadsRdkit {
  Util.loadRdkit()
}

object Util extends HasLogging {

  def loadRdkit(): Unit = {

    if (!System.getenv().containsKey("RDBASE")) {
      val msg = "RDBASE environment variable not set: rdkit will not start if it is not set to rdkit distribution directory"
      logger.error(msg)
      throw new IllegalArgumentException(msg)
    }

    try {
      System.loadLibrary("GraphMolWrap")
    } catch {
      case _: UnsatisfiedLinkError =>
        logger.warn("Unable to load RDKit library from java.library.path")
        val libDir = getProjectFilePath("lib")
        val libPatt = "*raphMolWrap*"
        val fileFilter = new WildcardFileFilter(libPatt).asInstanceOf[FileFilter]
        val matchingFiles = libDir.listFiles(fileFilter)
        if (matchingFiles == null || matchingFiles.isEmpty) {
          val msg = s"No match for library file $libPatt in project library $libDir"
          logger.error(msg)
          throw new RuntimeException(msg)
        }
        val libFile = matchingFiles(0).getAbsolutePath
        System.load(libFile)
    }
  }

  def getClassPath: String = {
    val path = new File(getClass.getProtectionDomain.getCodeSource.getLocation.toURI.getPath)
    path.getParent
  }

  def getProjectRoot: File = {
    new File(getClassPath).getParentFile.getCanonicalFile
  }

  def getProjectFilePath(filename: String): File = {
    if (filename.startsWith("/")) {
      new File(filename).getCanonicalFile
    } else {
      new File(getProjectRoot, filename).getCanonicalFile
    }
  }

  def setDefaultFonts(size: Integer): Unit = {
    UIManager.getLookAndFeelDefaults.keySet().asScala
      .foreach { k =>
        if (k.toString.toLowerCase.contains("font")) {
          val font = UIManager.getDefaults.getFont(k)
          if (font != null) {
            UIManager.put(k, font.deriveFont(size.toFloat))
          }
        }
      }
  }

  def isHiDpi: Boolean = {
    val size = Toolkit.getDefaultToolkit.getScreenSize
    size.height > 2000
  }


}

