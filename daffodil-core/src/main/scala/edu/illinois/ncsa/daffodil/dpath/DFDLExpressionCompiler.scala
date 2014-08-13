package edu.illinois.ncsa.daffodil.dpath

/* Copyright (c) 2012-2014 Tresys Technology, LLC. All rights reserved.
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

import edu.illinois.ncsa.daffodil.ExecutionMode
import edu.illinois.ncsa.daffodil.dsom.oolag.OOLAG.OOLAGHost
import edu.illinois.ncsa.daffodil.util.DebugRegexParsers
import edu.illinois.ncsa.daffodil.exceptions._
import edu.illinois.ncsa.daffodil.dsom._
import scala.xml.NamespaceBinding
import edu.illinois.ncsa.daffodil.xml._
import edu.illinois.ncsa.daffodil.processors._
import edu.illinois.ncsa.daffodil.xml.RefQName
import scala.util.parsing.input.CharSequenceReader

//class DFDLExpressionError(schemaContext: Option[SchemaFileLocatable],
//  kind: String,
//  args: Any*)
//  extends SchemaDefinitionDiagnosticBase(
//    schemaContext, None, None, kind, args: _*) {
//
//  override def isError = false
//  val diagnosticKind = "Error"
//
//  override def contextInfo(msg: String,
//    diagnosticKind: String,
//    schContextLocDescription: String,
//    annContextLocDescription: String,
//    schemaContext: Option[SchemaFileLocatable]): String = {
//
//    val res = "DFDLExpressionError " + diagnosticKind + ": " + msg +
//      "\nSchema context: " + schemaContext.getOrElse("top level") + "." +
//      // TODO: should be one or the other, never(?) both
//      schContextLocDescription +
//      annContextLocDescription
//
//    res
//  }
//}
//
//// For debugging to indent text
//object StepCounter {
//  private var cntr: Int = 0
//  def plusOne(): Int = {
//    cntr = cntr + 1
//    cntr
//  }
//  def minusOne(): Int = {
//    if (cntr > 0) cntr = cntr - 1
//    cntr
//  }
//}

/**
 * Parses DPath expressions. Most real analysis is done later. This is
 * just the syntax being legal so that we can build the abstract syntax
 * tree (of ElementBase-derived classes).
 *
 * Use isEvaluatedAbove for expressions that are evaluated in a parent context
 * around the element where they are expressed (e.g., occursCount)
 *
 * One goal of this object, and the reason it is yet another separate
 * compiler object, is that it uses Scala's Combinator parsers, which
 * have been known to cause memory leaks. This class is transient. We never
 * save it. So hopefully that discards all the state of the combinator
 * stuff as well.
 */
class DFDLPathExpressionCompiler(
  nodeInfoKind: NodeInfo.Kind,
  namespaces: NamespaceBinding,
  context: DPathCompileInfo,
  isEvaluatedAbove: Boolean = false) extends DebugRegexParsers {

  def compile(expr: String): CompiledExpression = {
    val tree = getExpressionTree(expr)

    val recipe = tree.compiledDPath

    val value = recipe.runExpressionForConstant(Some(context))
    val res = value match {
      case Some(constantValue) => {
        Assert.invariant(constantValue != null)
        val res = new ConstantExpression(nodeInfoKind, constantValue)
        res
      }
      case None => {
        new RuntimeExpressionDPath(nodeInfoKind, recipe, expr, context, isEvaluatedAbove)
      }
    }
    res
  }

  override val skipWhitespace = true

  // only used to determine if paths are constant or not. 
  // private val expressionCompiler = new ExpressionCompiler(context)

  /**
   *  We need to override ~ of Parser so that we can consume/omit
   *  whitespace separating sub-expressions.
   *
   *  Before this fix the following would occur:
   *
   *  Given:
   * 	{ ../../e1 eq 1 }
   *
   *  Was interpreted as:
   * 	{ ../../e1eq1 }
   *
   *  This was incorrect.
   */
  override def Parser[T](f: Input => ParseResult[T]): Parser[T] = new Parser[T] {
    def apply(in: Input) = f(in)

    def WS: Parser[String] = """\s""".r
    def OptRepWhiteSpace: Parser[Any] = WS.*

    override def ~[U](q: => Parser[U]): Parser[~[T, U]] = {
      lazy val p = q // lazy argument
      (for (a <- this; x <- OptRepWhiteSpace; b <- p) yield new ~(a, b)).named("~")
    }
  }

  /**
   * A helper method that turns a `Parser` into one that will
   *  print debugging information to stdout before and after
   *  being applied.
   */
  val verboseParse = false // true if you want to see the expression parsing

  override def log[T](p: => Parser[T])(name: String): Parser[T] =
    if (!verboseParse) p
    else Parser { in =>
      Console.out.println("trying %s at %s".format(name, in))
      val r = p(in)
      Console.out.println("end %s --> %s".format(name, r))
      r
    }

  /**
   * DFDL has various restrictions on path expressions. This extracts the paths from the expression
   * so that one can check whether their names are all meaningful (for example).
   */
  def getPathsFromExpression(expr: String, vmap: Any): Either[(String, Any), (List[Expression], Any)] = ???
  //
  // The tests which call this function will be replaced by work done by the 
  // DPath compiler generally to insure that paths are sensible.
  // at that point, delete this code.  
  //    variableMap = vmap
  //    val pResult = this.parse(this.log(DFDLExpression)("getPathsFromExpression"), expr)
  //    pResult match {
  //      case Success(paths, next) => Right(paths.getPathExpressions, variableMap)
  //      case NoSuccess(msg, next) => Left(msg, variableMap)
  //    }
  //  }
  //
  //  def getPathsFromExpressionAsCompiledExpressions(expr: String, vmap: VariableMap): Either[(String, VariableMap), (List[CompiledExpression], VariableMap)] = {
  //    variableMap = vmap
  //    val pResult = this.parse(this.log(DFDLExpression)("getPathsFromExpressionAsCompiledExpressions"), expr)
  //    pResult match {
  //      case Success(paths, next) => {
  //        val ces = paths.getPathExpressions.map { p =>
  //          {
  //            val exp = "{ " + p.toString + " }"
  //            val f = Found(exp, context)
  //            val ce = expressionCompiler.compile(f)
  //            ce
  //          }
  //        }.filterNot(ce => ce.isConstant) // Paths are not constant
  //        Right(ces, variableMap)
  //      }
  //      case NoSuccess(msg, next) => Left(msg, variableMap)
  //    }
  //  }

  def getExpressionTree(expr: String): WholeExpression = {
    // This wrapping of phrase() prevents a memory leak in the scala parser
    // combinators in scala 2.10. See the following bug for more information:
    //
    //   https://issues.scala-lang.org/browse/SI-4929
    //
    // The phrase() function does not have the memory leak problem, however, it
    // requires that the parse consumes all the data and succeeds. So we have
    // to fake that. This is done by wrapping the real result in a Success
    // (this is done by the wrapAsSuccess() function). It also adds a
    // SucessAtEnd parser which always succeeds and always consumes all of its
    // data (which is just the empty string). This way, the whole parse succeeds
    // and it appears to have consumed all the data. Once this is complete, we
    // then unwrap the captured result (which may be a Success or NoSuccess)
    // and inspect that to determine that actual result of the parse.
    //
    // Once the above bug is fixed, the phrase, wrapAsSuccess, and SuccessAtEnd
    // stuff should all go away.

    val pResult = this.parse(phrase(wrapAsSuccess(TopLevel) ~ SuccessAtEnd), expr)

    val realResult = pResult match {
      case Success(res, _) => {
        val wrappedResult ~ _ = res
        wrappedResult
      }
      case ns: NoSuccess => ns // This should never happen
    }

    realResult match {
      case Success(expressionAST, next) => {
        expressionAST.init()
        expressionAST
      }
      case NoSuccess(msg, next) => {
        // blech - no easy way to just grab up to 30 chars from a Reader[Char]
        var nextRdr = next
        var nextString = new StringBuffer()
        var i = 0
        while (!nextRdr.atEnd & i < 30) {
          nextString.append(nextRdr.first)
          nextRdr = nextRdr.rest
          i += 1
        }
        context.SDE("Unable to parse expression. Message: %s\nNext: %s.", msg, nextString.toString())
      }
    }
  }

  def wrapAsSuccess[T](p: => Parser[T]): Parser[ParseResult[T]] = Parser { in =>
    p(in) match {
      case ns: NoSuccess => Success(ns, in)
      case _@ s => Success(s, in)
    }
  }

  /*
   * The grammar defined below does not match up with the expression language
   * grammar in the DFDL spec. The one in the spec is specifically designed
   * to just be a variation on XPath's published grammar. But DFDL expressions
   * are simpler and so a simpler grammar will suffice.
   */

  def ContextItemExpr = "." ^^ { expr => Self(None) }
  def AbbrevReverseStep = ".." ^^ { expr => Up(None) }
  // TODO support forward axis syntax some day
  // def ForwardAxis = ("child" ~ "::") | ("self" ~ "::") 
  def EqualityComp = "eq" | "ne" | "=" | "!="
  def NumberComp = "lt" | "le" | "gt" | "ge" | "<" | ">" | "<=" | ">="
  def Comp = EqualityComp | NumberComp
  //
  // we don't care if it has braces around it or not.
  def TopLevel: Parser[WholeExpression] = ("{" ~> Expr <~ "}" | Expr) ^^ { xpr =>
    WholeExpression(nodeInfoKind, xpr, namespaces, context)
  }

  val SuccessAtEnd = Parser { in => Success(in, new CharSequenceReader("")) }

  def Expr: Parser[Expression] = ExprSingle
  def ExprSingle: Parser[Expression] = IfExpr | OrExpr

  def IfExpr: Parser[Expression] = log(
    "if" ~> "(" ~> (Expr <~ ")") ~ ("then" ~> ExprSingle) ~ ("else" ~> ExprSingle) ^^ {
      case tst ~ th ~ els =>
        IfExpression(List(tst, th, els))
    })("if")
  //
  // I think structuring the grammar rules this way implements proper
  // operator precedence for XPath (and DPath is the same).
  //
  def OrExpr: Parser[Expression] = log(
    AndExpr ~ ("or" ~> AndExpr).* ^^ {
      case a1 ~ Nil => a1
      case a1 ~ aMore => OrExpression(a1 :: aMore)
    })("or")

  def AndExpr: Parser[Expression] = log(
    ComparisonExpr ~ ("and" ~> ComparisonExpr).* ^^ {
      case a1 ~ Nil => a1
      case a1 ~ aMore => AndExpression(a1 :: aMore)
    })("and")

  def ComparisonExpr = log(AdditiveExpr ~ (Comp ~ AdditiveExpr).? ^^ { x =>
    x match {
      case a1 ~ Some(vc ~ a2) => {
        if (List("eq", "ne", "=", "!=").contains(vc))
          EqualityComparisonExpression(vc, List(a1, a2))
        else
          NumberComparisonExpression(vc, List(a1, a2))
      }
      case a1 ~ None => a1
    }
  })("compare")

  def AdditiveExpr: Parser[Expression] = log(
    MultiplicativeExpr ~ (("+" | "-") ~ MultiplicativeExpr).* ^^ {
      case m1 ~ mMore => mMore.foldLeft(m1) { case (a, op ~ b) => AdditiveExpression(op, List(a, b)) }
    })("add")

  def MultiplicativeExpr: Parser[Expression] = log(
    UnaryExpr ~ (("*" | "div" | "idiv" | "mod") ~ UnaryExpr).* ^^ {
      case u1 ~ uMore => uMore.foldLeft(u1) { case (a, op ~ b) => MultiplicativeExpression(op, List(a, b)) }
    })("mult")

  def UnaryExpr: Parser[Expression] = log(
    ("-" | "+").? ~ ValueExpr ^^ {
      case Some(op) ~ v => UnaryExpression(op, v)
      case None ~ v => v
    })("unary")

  def ValueExpr = log(PrimaryExpr | PathExpr)("value")

  def PathExpr: Parser[PathExpression] = log(
    ("/" ~> RelativePathExpr) ^^ { r => RootPathExpression(Some(r)) } |
      ("/") ^^ { r => RootPathExpression(None) } |
      RelativePathExpr)("path")

  def RelativePathExpr: Parser[RelativePathExpression] = log(
    StepExpr ~ ("/" ~> StepExpr).* ^^ { case s1 ~ moreSteps => RelativePathExpression(s1 :: moreSteps, isEvaluatedAbove) })("relativePath")

  def StepExpr: Parser[StepExpression] = log(AxisStep)("step")
  def AxisStep: Parser[StepExpression] =
    ".." ~> Predicate.? ^^ { Up(_) } |
      "." ~> Predicate.? ^^ { Self(_) } |
      StepName ~ Predicate.? ^^ { case qn ~ p => { NamedStep(qn, p) } }

  def Predicate: Parser[PredicateExpression] = log(
    "[" ~> Expr <~ "]" ^^ { PredicateExpression(_) })("predicate")

  def PrimaryExpr: Parser[PrimaryExpression] = log(
    FunctionCall | Literal | VarRef | ParenthesizedExpr)("primary")

  def Literal = log((StringLiteral | NumericLiteral) ^^ { LiteralExpression(_) })("literal")

  def NumericLiteral = DoubleLiteral | DecimalLiteral | IntegerLiteral

  def VarRef = "$" ~> RefName ^^ { VariableRef(_) }

  def ParenthesizedExpr = "(" ~> Expr <~ ")" ^^ { ParenthesizedExpression(_) }

  def FunctionCall: Parser[FunctionCallExpression] = log(
    (RefName ~ ArgList) ^^ {
      case qn ~ arglist => FunctionCallExpression(qn, arglist)
    })("functionCall")

  def ArgList = log(
    "(" ~ ")" ^^ { _ => Nil } |
      "(" ~> (ExprSingle ~ (("," ~> ExprSingle).*)) <~ ")" ^^ { case e1 ~ moreEs => e1 :: moreEs })("argList")

  def StepName = log(QualifiedName)("stepName")

  def RefName = log(QualifiedName)("refName")

  def QualifiedName: Parser[String] = PrefixedName | UnprefixedName

  def PrefixedName = QNameRegex
  def UnprefixedName = NCNameRegex

  def IntegerLiteral: Parser[BigInt] = Digits ^^ { BigInt(_) }

  val Digits = """[0-9]+""".r
  val optDigits: Parser[String] = """[0-9]*""".r
  val Expon: Parser[String] = """[eE][+-]?[0-9]{1,3}""".r
  val plusMinus: Parser[String] = """[+-]?""".r

  val DecimalLiteral: Parser[BigDecimal] =
    ("." ~> Digits) ^^ { case dig => BigDecimal("0." + dig) } |
      (Digits ~ ("." ~> optDigits)) ^^ { case digit ~ optDig => BigDecimal(digit + "." + optDig) }

  val DoubleLiteral: Parser[Double] = (
    "." ~> Digits ~ Expon ^^ {
      case fraction ~ exp => {
        "0." + fraction + exp
      }
    } |
    Digits ~ (("." ~> optDigits).?) ~ Expon ^^ {
      case intPart ~ fraction ~ exp => intPart + "." + fraction.getOrElse("0") + exp
    }) ^^ { str => println(str); java.lang.Double.parseDouble(str) }

  /**
   * String literal must be one regex, not separate combinators combined.
   *
   * This is to avoid whitespace collapsing inside string literals. We want
   * whitespace to be ignored outside string literals, but not inside them.
   */
  //  val StringLiteral: Parser[String] =
  //    ("\"" ~> (EscapeQuot | notQuot).* <~ "\"") ^^ { case values => values.mkString } |
  //      ("'" ~> (EscapeApos | notApos).* <~ "'") ^^ { case values => values.mkString }
  //
  //  val EscapeQuot = "\"\""
  //  val EscapeApos = "''"
  //  val notQuot: Parser[String] = """[^"]""".r
  //  val notApos: Parser[String] = """[^']""".r

  val StringLiteral: Parser[String] = {
    val escapeQuot = """\"\""""
    val notQuot = """[^"]"""
    val escapeApos = """''"""
    val notApos = """[^']"""
    def quotedBody(esc: String, not: String) = """(%s|%s)*""".format(esc, not)
    val doubleQuotedBody = quotedBody(escapeQuot, notQuot)
    val singleQuotedBody = quotedBody(escapeApos, notApos)
    val stringLit = """(\"%s\")|(\'%s\')""".format(doubleQuotedBody, singleQuotedBody)
    val stringLitRegex = stringLit.r
    stringLitRegex ^^ { sl =>
      if (sl.length == 2) "" // turns into empty string
      else sl.substring(1, sl.length - 1) // 2nd arg is endPos, not how many chars.
    }
  }

  val xC0_D6 = ("""[\x{C0}-\x{D6}]""")
  val xD8_F6 = """[\x{D8}-\x{F6}]"""
  val xF8_2FF = """[\x{F8}-\x{2FF}]"""
  val x370_37D = """[\x{370}-\x{37D}]"""
  val x37F_1FFF = """[\x{37F}-\x{1FFF}]"""
  val x200C_200D = """\x{200c}|\x{200d}"""
  val x2070_218F = """[\x{2070}-\x{218F}]"""
  val x2C00_2FEF = """[\x{2C00}-\x{2FEF}]"""
  val x3001_D7FF = """[\x{3001}-\x{D7FF}]"""
  val xF900_FDCF = """[\x{F900}-\x{FDCF}]"""
  val xFDF0_FFFD = """[\x{FDF0}-\x{FFFD}]"""
  val x10000_EFFFF = """[\x{10000}-\x{EFFFF}]"""
  val range0_9 = """[0-9]"""
  val xB7 = """\xB7"""
  val x0300_036F = """[\x{0300}-\x{036F}]"""
  val x203F_2040 = """[\x{203F}-\x{2040}]"""

  val ncNameStartChar = """[A-Z]|_|[a-z]""" + "|" + xC0_D6 + "|" + xD8_F6 + "|" + xF8_2FF + "|" + x37F_1FFF + "|" + x200C_200D + "|" +
    x2070_218F + "|" + x2C00_2FEF + "|" + x3001_D7FF + "|" + xF900_FDCF + "|" + xFDF0_FFFD + "|" + x10000_EFFFF
  val ncNameChar = ncNameStartChar + "|" + "\\-" + "|" + "\\." + "|" + range0_9 // + "|" + xB7 + "|" + x0300_036F + "|" + x203F_2040
  val NCNameRegexString = ("(" + ncNameStartChar + ")((" + ncNameChar + ")*)")
  val NCNameRegex = NCNameRegexString.r
  val QNameRegex = (NCNameRegexString + ":" + NCNameRegexString).r

}