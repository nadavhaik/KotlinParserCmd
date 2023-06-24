package com.parser


interface Exp

interface SExp: Exp
interface ScopeExp: Exp

class ExpertScope(val body: Results<ScopeExp>?) {
    val tag = "ExpertScope"
}

typealias None = Unit

class ExpertNumber(val value: Number) : SExp {
    val tag = "ExpertNumber"

    fun negative(): ExpertNumber {
        if(value is Int) {
            return ExpertNumber(-value.toInt())
        }
        return ExpertNumber(-value.toFloat())
    }
}

typealias ExpertPrimitive = ExpertNumber
typealias ExpertBool = Boolean
interface StringPart

class FormattedSExp(val leftAt: ParsingResult<String>, val sExp: ParsingResult<SExp>, rightAt: ParsingResult<String>): StringPart {
    val tag = "FormattedSExpression"
}
class RawStringPart(val value: String): StringPart {
    val tag = "RawStringPart"
}

class ExpertString(val leftQuotation: ParsingResult<String>, val value: Results<StringPart>?,
                   val rightQuotation: ParsingResult<String>) : SExp {
   val tag = "ExpertString"
}

class VarRef(val name: String): SExp, ScopeExp {
    val tag = "VarRef"
}

class VarAssignment(val lval: ParsingResult<String>, val isWord: ParsingResult<ReservedWord>, val rval: ParsingResult<SExp>): ScopeExp {
    val tag = "VarAssignment"
}

abstract class ReservedWord(val value: String) : ScopeExp

class OperatorReservedWord(value: String) : ReservedWord(value) {
    val tag = "OperatorReservedWord"
}
class OtherReservedWord(value: String) : ReservedWord(value) {
    val tag = "OtherReservedWord"
}


const val PROCESS = "PROCESS"
const val IS = "IS"
const val PLUS = "PLUS"
const val MINUS = "MINUS"
const val MUL = "MULT"
const val DIV = "DIV"
const val LESS_THAN = "LESS"
const val LESS_OR_EQUAL = "LESS EQ"
const val MORE_THAN = "GREAT"
const val MORE_OR_EQUAL = "GREAT EQ"
const val OR = "OR"
const val AND = "AND"
const val NOT = "NOT"
const val IN = "IS MEMBER"
const val EQUALS = "EQ"
const val NOT_EQUALS = "NOT EQ"
const val FIRST_IN = "FIRST FROM"
const val SECOND_IN = "SECOND FROM"
const val MAKE_EMPTY = "MAKE EMPTY"
const val NOT_EMPTY = "IS NOT EMPTY"
const val IS_EMPTY = "IS EMPTY"
const val OF = "OF"
const val DO = "DO"
const val IF = "IF"
const val THEN = "THEN"
const val ELIF = "ELSE IF"
const val ELSE = "ELSE"
const val END_IF = "END IF"
const val FOREACH = "FOR ALL"
const val FROM = "FROM"
const val END_LOOP = "END LOOP"
const val BREAK_PROCESS = "EXIT"
const val BREAK_LOOP = "EXIT LOOP"
const val ADD = "ADD"
const val TO = "TO"
const val REMOVE = "REMOVE"
const val LENGTH = "LENGTH"
const val INPUT = "INPUT"
const val OUTPUT = "OUTPUT"
const val INPUT_OUTPUT = "INPUT OUTPUT"
const val START_PROCESS = "START PROCESS"
const val END_PROCESS = "END PROCESS"

fun isOperatorReservedWordName(x: Any): Boolean {
    return x === PLUS || x === MINUS || x === MUL || x === DIV || x === LESS_THAN || x === LESS_OR_EQUAL || x === MORE_THAN ||
            x === MORE_OR_EQUAL || x === OR || x === AND || x === NOT || x === IN || x === EQUALS || x === NOT_EQUALS ||
            x === FIRST_IN || x === SECOND_IN || x === MAKE_EMPTY || x === NOT_EMPTY || x === IS_EMPTY || x === LENGTH || x === ADD || x === REMOVE
}

fun isOtherReservedWordName(x: Any): Boolean {
    return x === PROCESS || x === IS || x === OF || x === DO || x === IF || x === THEN || x === ELIF || x === ELSE ||
        x === END_IF || x === FOREACH || x === FROM || x === END_LOOP || x === BREAK_PROCESS || x === BREAK_LOOP ||
        x === TO || x === INPUT || x === OUTPUT || x === INPUT_OUTPUT || x === START_PROCESS || x === END_PROCESS;
}

fun createReservedWord(name: String): ReservedWord {
    if(isOperatorReservedWordName(name)) {
        return OperatorReservedWord(name)
    } else if (isOtherReservedWordName(name)) {
        return OtherReservedWord(name)
    }
    throw IllegalArgumentException("Unrecognized reserved word: $name")
}

class ExpertSExpressionInBrackets(val leftBracket: ParsingResult<String>, val sexp: ParsingResult<SExp>,
                                  val rightBracket: ParsingResult<String>): SExp {
    val tag = "ExpertSExpressionInBrackets"
}

interface ExpertApplication: SExp, ScopeExp
class PrimitiveApplication(val rator: ParsingResult<OperatorReservedWord>,
                            val parameters: Results<SExp>?=null,
                            val contextWord: ParsingResult<OtherReservedWord>?=null): ExpertApplication {
    val tag = "PrimitiveApplication"
}

fun signToRator(sign: String): String {
    return when(sign) {
        "+" -> PLUS
        "-" -> MINUS
        "*" -> MUL
        "/" -> DIV
        "<" -> LESS_THAN
        "<=" -> LESS_OR_EQUAL
        ">" -> MORE_THAN
        ">=" -> MORE_OR_EQUAL
        "=" -> EQUALS
        "!=" -> NOT_EQUALS
        else -> throw IllegalArgumentException("Illegal sign: $sign")
    }
}

fun signToReservedWord(sign: String): OperatorReservedWord {
    return OperatorReservedWord(signToRator(sign))
}
class UnrecognizedExpression: ScopeExp {
    val tag = "UnrecognizedExpression"
}
class LibraryApplication(val hebrewName: ParsingResult<VarRef>, val knownParameters: Results<SExp>?,
                         val ofWords: Results<ReservedWord>?): ExpertApplication  {
    val tag = "LibraryApplication"
}
class ExpertProcessCall(val hebrewName: ParsingResult<VarRef>, val doWord: ParsingResult<ReservedWord>,
                        val ofWords: Results<ReservedWord>?, val knownParameters: Results<SExp>?): ScopeExp {
    val tag = "ExpertProcessCall"
}
class ExpertForEachExp(val forEachWord: ParsingResult<ReservedWord>, val loopVar: ParsingResult<VarRef>,
                       val fromWord: ParsingResult<ReservedWord>, val list: ParsingResult<VarRef>,
                       val body: ParsingResult<ExpertScope>, val endLoopWord: ParsingResult<ReservedWord>?): ScopeExp {
    val tag = "ExpertForEachExpression"
}
class ExpertIfThenExpression(val ifWord: ParsingResult<ReservedWord>, val test: ParsingResult<SExp>,
                             val body: ParsingResult<ExpertScope>, val thenWord: ParsingResult<ReservedWord>?) {
    val tag = "ExpertIfThenExpression"
}
class ExpertElifExpression(val elifWord: ParsingResult<ReservedWord>, val test: ParsingResult<SExp>,
                           val body: ParsingResult<ExpertScope>, val thenWord: ParsingResult<ReservedWord>?) {
    val tag = "ExpertElifExpression"
}
class ExpertElseExpression(val elseWord: ParsingResult<ReservedWord>, val body: ParsingResult<ExpertScope>) {
    val tag = "ExpertElseExpression";
}
class ExpertFullIfExpression(val ifExp: ParsingResult<ExpertIfThenExpression>, val elifs: Results<ExpertElifExpression>?,
                             val elseExp: ParsingResult<ExpertElseExpression>?, val endIf: ParsingResult<ReservedWord>?): ScopeExp {
    val tag = "ExpertFullIfExpression"
}

// End of SExps and ScopeExps

class ProcessName(val processWord: ParsingResult<ReservedWord>, val name: ParsingResult<String>) {
    val tag = "ProcessName"
}
class TypeHint(val leftBracket: ParsingResult<String>, val content: ParsingResult<String>,
               val rightBracket:  ParsingResult<String>) {
    val tag = "TypeHint"
}
class HeaderVar(val name: ParsingResult<String>, val typeHint: ParsingResult<TypeHint>? = null) {
    val tag = "HeaderVar"
}
class HeaderVarLite(val name: String, val typeHint: String?) {
    val tag = "HeaderVarLite";
}

fun litify(headerVar: HeaderVar): HeaderVarLite {
    val typeHint = if(headerVar.typeHint  != null) headerVar.typeHint.found.content.found else null
    return HeaderVarLite(headerVar.name.found, typeHint)
}
class HeaderVariables(val type: ParsingResult<ReservedWord>, val vars: Results<HeaderVar>?) {
    val tag = "HeaderVariables"
}
class ProcessHeader(val hebrewName: ParsingResult<ProcessName>, val inputs: ParsingResult<HeaderVariables>?,
                      val outputs: ParsingResult<HeaderVariables>?, val inputs_outputs: ParsingResult<HeaderVariables>?,
                      val unrecognized: Results<UnrecognizedExpression>?) {
    val tag = "ProcessHeader"
}
class ProcessHeaderLite(val englishName: String, val hebrewName: String, val inputs: List <HeaderVarLite>,
                        val outputs: List<HeaderVarLite>, val input_outputs: List<HeaderVarLite>,
                        val documentation: String) {
    val tag = "ProcessHeaderLite"
}

fun litify(englishName: String, header: ProcessHeader, documentation: String): ProcessHeaderLite {
    val inputs = if(header.inputs == null || header.inputs.found.vars == null) ArrayList() else  header.inputs.found.vars.found.map { res -> litify(res.found) }
    val outputs = if(header.outputs == null || header.outputs.found.vars == null) ArrayList() else  header.outputs.found.vars.found.map { res -> litify(res.found) }
    val inputs_outputs = if(header.inputs_outputs == null || header.inputs_outputs.found.vars == null) ArrayList() else  header.inputs_outputs.found.vars.found.map { res -> litify(res.found) }

    return ProcessHeaderLite(englishName, header.hebrewName.found.name.found, inputs, outputs, inputs_outputs, documentation)
}
class ParsedProcess(val header: ParsingResult<ProcessHeader>?, val startWord: ParsingResult<ReservedWord>?,
                     val body: ParsingResult<ExpertScope>,
                     val endWord: ParsingResult<ReservedWord>?) {
    val tag = "ParsedProcess"
}
class ExpertComment() {
    val tag = "ExpertComment"
}
class ParameterContent(val param: ParsingResult<SExp>, val ofWord: ParsingResult<ReservedWord>? = null) {
    val tag = "ParameterContent"
}

data class ExpertProcess(val hebrewName: String, val englishName: String, val documentation: String,
    val inputs: List<HeaderVarLite>, val outputs: List<HeaderVarLite>, val inputsOutputs: List<HeaderVarLite>)
