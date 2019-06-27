package com.cairn.common

import java.io.{LineNumberReader, Reader}
import java.sql.{Connection, SQLException}

import org.apache.commons.lang3.StringUtils

import scala.collection.JavaConverters._

class SqlScriptRunner(val stopOnError: Boolean = true, val autoCommit: Boolean = false,
                      val sqlVariables: Map[String, String] = Map.empty) extends HasLogging {

  def this(stopOnError: Boolean, autoCommit: Boolean,
           sqlVariables: java.util.Map[String, String]) {
    this(stopOnError, autoCommit, sqlVariables.asScala.toMap)
  }

  private val sqlDelimiter = ";"
  private val plSqlDelimiter = "/"
  private val commandBuffer = new StringBuilder
  private val plSqlStarts = Vector(
    """^create\s+.*\spackage""".r,
    """^create\s+.*\stype""".r,
    """^declare\b""".r,
    """^begin\b""".r
  )

  @throws(classOf[SQLException])
  def processScript(connection: Connection, reader: Reader):Unit = {
    val lineReader = new LineNumberReader(reader)

    var readingPlSql = false
    val plSqlComments = Array("--", "//", "rem")

    try {
      Iterator.continually(lineReader.readLine()).takeWhile(_ != null)
        .foreach { line =>
          val trimmedLine = line.trim.toLowerCase
          if (!readingPlSql)
            readingPlSql = checkPlSqlStart(trimmedLine)
          if (readingPlSql) {
            if (trimmedLine.equals(plSqlDelimiter)) {
              executeCommand(connection, readingPlSql)
              readingPlSql = false
            }
            else {
              addPlSqlLineToCommand(line)
            }
          } else {
            val commentLine = plSqlComments.exists(trimmedLine.startsWith)
            if (trimmedLine.nonEmpty && !commentLine) {
              addSqlLineToCommand(line)
              if (trimmedLine.endsWith(sqlDelimiter))
                executeCommand(connection, readingPlSql)
            }
          }
        }
    }
    finally {
      reader.close()
    }
  }

  private def checkPlSqlStart(trimmedLine: String) = {
    val testLine = trimmedLine.toLowerCase

    plSqlStarts
      .exists { patt =>
        patt.findFirstMatchIn(testLine) match {
          case None => false
          case Some(_) => true
        }
      }
  }

  private def addSqlLineToCommand(line: String) = {
    if (sqlVariables.nonEmpty && line.indexOf('&') >= 0) {
      val pattern = """(&\w+)""".r
      val replacedVariablesLine = pattern.replaceAllIn(line, m => {
        val token = m.group(1).substring(1)
        if (!sqlVariables.contains(token)) {
          val msg = s"Unable to find substitution for variable $token in $line"
          logger.error(msg)
          throw new IllegalArgumentException(msg)
        }
        sqlVariables(token)
      })
      commandBuffer.append(replacedVariablesLine)
    } else {
      commandBuffer.append(line)
    }
    if (!commandBuffer.endsWith(sqlDelimiter))
      commandBuffer.append("\n")
  }

  private def addPlSqlLineToCommand(line: String) = {
    commandBuffer.append(line)
    commandBuffer.append("\n")
  }

  private def executeCommand(connection: Connection, readingPlSql: Boolean):Unit = {
    var command = commandBuffer.toString()
    command = command.trim
    if (!readingPlSql && command.endsWith(sqlDelimiter))
      command = command.substring(0, command.length - sqlDelimiter.length)
    else if (readingPlSql && command.endsWith(plSqlDelimiter))
      command = command.substring(0, command.length - plSqlDelimiter.length)

    commandBuffer.clear()

    val statement = connection.createStatement()
    val commandSummary = StringUtils.abbreviate(command, 100)
    try {
      logger.info(s"executing command $commandSummary")
      statement.execute(command)
      if (autoCommit)
        connection.commit()
    }
    catch {
      case ex: SQLException =>
        val msg = s"SQL error executing command $commandSummary"
        if (stopOnError) {
          connection.rollback()
          logger.error(msg, ex)
          throw ex
        } else {
          logger.warn(msg, ex)
        }
    }
  }

}
