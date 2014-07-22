package edu.illinois.ncsa.daffodil.processors.dfa

import scala.collection.mutable.ArrayBuffer

import edu.illinois.ncsa.daffodil.processors.CharDelim
import edu.illinois.ncsa.daffodil.processors.DelimBase
import edu.illinois.ncsa.daffodil.processors.Delimiter
import edu.illinois.ncsa.daffodil.processors.NLDelim
import edu.illinois.ncsa.daffodil.processors.WSPDelim
import edu.illinois.ncsa.daffodil.processors.WSPPlusDelim
import edu.illinois.ncsa.daffodil.processors.WSPStarDelim

object CreateDelimiterMatcher {
  def apply(delimiters: Seq[DFADelimiter]): DelimsMatcher = {
    new DelimsMatcherImpl(delimiters)
  }
}

object CreateDelimiterDFA {

  /**
   * Constructs an Array of states reflecting the delimiters only.
   * StateNum is offset by stateOffset
   */
  protected def apply(delimiter: Seq[DelimBase], delimiterStr: String): DFADelimiter = {

    val allStates: ArrayBuffer[State] = ArrayBuffer.empty

    val initialState = buildTransitions(delimiter, allStates)

    new DFADelimiterImpl(allStates.reverse.toArray, delimiterStr)
  }

  /**
   * Converts a String to a DFA representing
   * that string
   */
  def apply(delimiterStr: String): DFADelimiter = {
    val d = new Delimiter()
    d.compile(delimiterStr)
    val db = d.delimBuf
    apply(db, delimiterStr)
  }

  /**
   * Converts a Seq of String to a Seq of
   * DFA's representing each String.
   */
  def apply(delimiters: Seq[String]): Seq[DFADelimiter] = {
    delimiters.map(d => apply(d))
  }

  /**
   * Returns a state representing the DelimBase object.
   */
  protected def getState(d: DelimBase, nextState: Int, stateNum: Int,
    allStates: ArrayBuffer[State]): DelimStateBase = {

    val theState = d match {
      case d: CharDelim => {
        new CharState(allStates, d.char, nextState, stateNum)
      }
      case d: WSPDelim => {
        new WSPState(allStates, nextState, stateNum)
      }
      case d: WSPStarDelim => {
        new WSPStarState(allStates, nextState, stateNum)
      }
      case d: WSPPlusDelim => {
        new WSPPlusState(allStates, nextState, stateNum)
      }
      case d: NLDelim => {
        new NLState(allStates, nextState, stateNum)
      }
    }
    theState
  }

  private def buildTransitions(delim: Seq[DelimBase],
    allStates: ArrayBuffer[State]): State = {
    assert(!delim.isEmpty)
    buildTransitions(null, delim.reverse, allStates)
  }

  private def buildTransitions(nextState: DelimStateBase, delim: Seq[DelimBase],
    allStates: ArrayBuffer[State]): State = {

    if (delim.isEmpty && nextState != null) {
      // We are initial state
      nextState.stateName = "StartState" //"PTERM0"
      return nextState
    }

    val currentState = getState(delim(0),
      if (nextState == null) DFA.FinalState else nextState.stateNum,
      delim.length - 1, allStates)
    val rest = delim.tail

    allStates += currentState
    return buildTransitions(currentState, rest, allStates)
  }
}