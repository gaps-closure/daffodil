package edu.illinois.ncsa.daffodil.exceptions

/* Copyright (c) 2012-2013 Tresys Technology, LLC. All rights reserved.
 *
 * Developed by: Tresys Technology, LLC
 *               http://www.tresys.com
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal with
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * 
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimers.
 * 
 *  2. Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimers in the
 *     documentation and/or other materials provided with the distribution.
 * 
 *  3. Neither the names of Tresys Technology, nor the names of its contributors
 *     may be used to endorse or promote products derived from this Software
 *     without specific prior written permission.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * CONTRIBUTORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS WITH THE
 * SOFTWARE.
 */

import java.net.URLDecoder

import scala.xml.Node

import edu.illinois.ncsa.daffodil.api.LocationInSchemaFile
import edu.illinois.ncsa.daffodil.xml.XMLUtils

trait SchemaFileLocatable extends LocationInSchemaFile {
  def lineAttribute: Option[String]
  def columnAttribute: Option[String]
  def fileAttribute: Option[String]

  lazy val lineNumber: Option[String] = lineAttribute match {
    case Some(seqNodes) => Some(seqNodes.toString)
    case None => None
  }

  lazy val lineDescription = lineNumber match {
    case Some(num) => " line " + num
    case None => ""
  }

  lazy val columnNumber = columnAttribute match {
    case Some(seqNodes) => Some(seqNodes.toString)
    case None => None
  }

  lazy val columnDescription = columnNumber match {
    case Some(num) => " column " + num
    case None => ""
  }

  // URLDecoder removes %20, etc from the file name.
  lazy val fileDescription = " in " + URLDecoder.decode(fileName, "UTF-8")

  lazy val locationDescription = {
    val showInfo = lineDescription != "" || fileDescription != ""
    val info = lineDescription + columnDescription + fileDescription
    val txt = if (showInfo) "Location" + info else ""
    txt
  }

  /**
   * It would appear that this is only used for informational purposes
   * and as such, doesn't need to be a URL.  Can just be String.
   *
   * override if you don't have a fileName attribute appended
   * but are in a context where some enclosing construct does
   * normally only a root node would have a file attribute.
   *
   * implement as
   * @example {{{
   *     lazy val fileName = fileNameFromAttribute()
   * }}}
   * or delegate like
   * @example {{{
   *     lazy val fileName = schemaDocument.fileName
   * }}}
   */
  def fileName: String

  def fileNameFromAttribute() = {
    fileAttribute match {
      case Some(seqNodes) => Some(seqNodes.toString)
      case None => None
    }

  }

}

