package com.parser

import java.util.*


open class ParsingError(message:String=""): Exception(message)
class NoMatchError(message:String = ""): ParsingError(message)
data class ParsingResult<T> (val indexFrom: Int, val indexTo: Int, val found: T) {
    fun length(): Int {
        return indexTo - indexFrom;
    }
}

enum class Severity {ERROR, WARNING, INFO}

data class DiagItem(val from: Int, val to: Int, val content: String, val severity: Severity)

typealias UniqueParser<T> = (String, Int, ScopeStack) -> ParsingResult<T>
typealias ListParser<T> = (String, Int, ScopeStack) -> List<ParsingResult<T>>
typealias Results<T> = ParsingResult<List<ParsingResult<T>>>

enum class ProcessScopeType {PROCESS, IF, LOOP}
class ScopeStack {
    private val stack: Stack<ProcessScopeType> = Stack()
    private var loopDepth: Int
    private var ifDepth: Int

    init {
        stack.push(ProcessScopeType.PROCESS)
        loopDepth = 0
        ifDepth = 0
    }


    fun enterIf() {
        stack.push(ProcessScopeType.IF)
        ifDepth++
    }

    fun enterLoop() {
        stack.push(ProcessScopeType.LOOP)
        loopDepth++
    }

    fun inLoop(): Boolean {
        return loopDepth > 0
    }

    fun inIf(): Boolean {
        return ifDepth > 0
    }

    fun exitScope() {
        when(stack.pop()) {
            ProcessScopeType.IF -> ifDepth--
            ProcessScopeType.LOOP -> loopDepth--
            else -> throw IllegalArgumentException("Couldn't pop scope!")
        }
    }
}
