package com.parser

import java.util.*
import kotlin.collections.ArrayList


fun charFromPred(pred: (String) -> Boolean): UniqueParser<String> {
    return fun(str: String, indexFrom: Int, _): ParsingResult<String> {
        val relevantChar = str.substring(indexFrom, indexFrom+1)
        if(indexFrom >= str.length || !pred(relevantChar)) {
            throw NoMatchError()
        }
        return ParsingResult(indexFrom, indexFrom+1, relevantChar)
    }
}


val whiteSpaceParser = word(" ")
val newlineParser = word("\n")
val carriageReturnParser = word("\r")
val formFeedParser = word("\u000c")
val tabParser = word("\t")
val backspaceParser = word("\b")

val notNoneSpacesParser: UniqueParser<String> = disjUnion(whiteSpaceParser, newlineParser, carriageReturnParser,
    formFeedParser, tabParser, backspaceParser)

val spaceParser = unitify(notNoneSpacesParser)

val englishLetterParser = charFromPred { str -> str.length == 1 &&
        ((str[0].code in 65..90) || (str[0].code in 97..122))}

val hebrewLetterParser = charFromPred { str -> str.length == 1 && str[0].code in 1488..1514}
val digitCharParser = charFromPred { str -> str.length == 1 && str[0].code in 48..57}

val commentsCharParser = charFromPred { str -> str.length == 1 && str[0] != '{' && str[0] != '}'}
fun commentsParserFun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertComment> {
    var nextIndexFrom = indexFrom
    nextIndexFrom = word("{")(str, nextIndexFrom, scopeStack).indexTo
    nextIndexFrom = unlistify(star(disjUnion(unitify( commentsCharParser), unitify { str, indexFrom, scopeStack -> commentsParserFun(str, indexFrom, scopeStack) })))(str, nextIndexFrom, scopeStack).indexTo
    nextIndexFrom = word("}")(str, nextIndexFrom, scopeStack).indexTo

    return ParsingResult(indexFrom, nextIndexFrom, ExpertComment())
}
val commentsParser: UniqueParser<ExpertComment> = {str, indexFrom, scopeStack -> commentsParserFun(str, indexFrom, scopeStack) }

val skipSpacesParser = unitify(unlistify(star(disjUnion(spaceParser, unitify(commentsParser)))))
val demandSpacesParser = unitify(unlistify(plus(disjUnion(spaceParser, unitify(commentsParser)))))


fun <T> spacedPlus(parser: UniqueParser<T>): ListParser<T> {
    return fun(str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<T>> {
        val first = parser(str, indexFrom, scopeStack)
        var rest: List<ParsingResult<T>> = ArrayList()
        try {
            val nextIndex = demandSpacesParser(str, first.indexTo, scopeStack).indexTo
            rest = spacedStar(parser)(str, nextIndex, scopeStack)
        } catch (_: NoMatchError) {}

        val res = ArrayList<ParsingResult<T>>()
        res.add(first)
        res.addAll(rest)

        return res
    }
}

fun <T> spacedStar(parser: UniqueParser<T>): ListParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<T>> {
        val restParser = star(concatAndPack(demandSpacesParser, parser) { space: Unit, res: T -> res })
        try {
            val first = parser(str, indexFrom, scopeStack)
            val rest = restParser(str, first.indexTo, scopeStack)
            val res = ArrayList<ParsingResult<T>>()
            res.add(first)
            res.addAll(rest)
            return res
        } catch (_: NoMatchError) {}
        return ArrayList()
    }
}

fun <T> makeSpaced(spacesParser: UniqueParser<Unit>, parsers: List<UniqueParser<T>>): ListParser<T> {
    return fun(str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<T>> {
        var nextIndexFrom = indexFrom
        val results = ArrayList<ParsingResult<T>>()
        for(i in parsers.indices) {
            val res = parsers[i](str, nextIndexFrom, scopeStack)
            results.add(res)
            if(i < parsers.size - 1) {
                nextIndexFrom = spacesParser(str, res.indexTo, scopeStack).indexTo
            }
        }

        return results
    }
}

fun <T> demandSpaces(vararg parsers: UniqueParser<T>): ListParser<T> {
    return makeSpaced(demandSpacesParser, parsers.asList())
}

fun <T> allowSpaces(vararg parsers: UniqueParser<T>): ListParser<T> {
    return makeSpaced(skipSpacesParser, parsers.asList())
}

fun reservedWordParser(rw: String): UniqueParser<ReservedWord> {
    return fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ReservedWord> {
        val translations = ReservedWordsData.getReservedWordTranslations(rw) ?: throw IllegalArgumentException()
        val ls2 = translations.map { trans -> spaceTerminatedWord(trans) }
        return pack(disjUnion(*ls2.toTypedArray())) { _: String -> createReservedWord(rw) }(str, indexFrom, scopeStack)
    }
}

fun ratorReservedWordParser(str: String): UniqueParser<OperatorReservedWord> {
    val rv = createReservedWord(str)
    if(rv !is OperatorReservedWord) {
        throw IllegalArgumentException("$str: not an operator reserved word!")
    }

    return castParser(reservedWordParser(str))
}

fun stringToNumber(p: UniqueParser<String>): UniqueParser<ExpertNumber> {
    return pack(p, fun(x: String): ExpertNumber {
        val intValue = x.toIntOrNull()
        if(intValue != null) {
            return ExpertNumber(intValue)
        }
        val floatValue = x.toFloatOrNull()
        if(floatValue != null) {
            return ExpertNumber(floatValue)
        }
        throw NoMatchError()
    })
}

val digitParser: UniqueParser<ExpertNumber> = stringToNumber(digitCharParser)
val digitsSequenceParser: UniqueParser<String> = stringPlus(digitCharParser)
val unsignedIntParser: UniqueParser<ExpertNumber> = stringToNumber(digitsSequenceParser)
val unsignedFloatParser: UniqueParser<ExpertNumber> =
    stringToNumber(concatStringParsers(digitsSequenceParser, word("."), stringStar(digitCharParser)))

val unsignedNumberParser: UniqueParser<ExpertNumber> = disjUnion(unsignedFloatParser, unsignedIntParser)
val signParser: UniqueParser<Boolean> =  optional(pack(words("+", "-")) { c -> c == "+" }, true)

val numberParser: UniqueParser<ExpertNumber> = concatAndPack(signParser, unsignedNumberParser)
{ sign: Boolean, num: ExpertNumber -> if(sign) num else num.negative() }

val unformattedStringPartParser: UniqueParser<RawStringPart> = pack(stringStar(charFromPred
    { str -> str.length == 1 && str[0] != '@' && str[0] != '"' && str[0] != '\n'})
    ) { str -> RawStringPart(str) }

val atSignParser = word("@")

val formattedStringPartParser: UniqueParser<FormattedSExp> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<FormattedSExp> {
    var nextIndexFrom = indexFrom

    val leftAt = atSignParser(str, nextIndexFrom, scopeStack)
    nextIndexFrom = skipSpacesParser(str, leftAt.indexTo, scopeStack).indexTo
    val sexp = sexpParser(str, nextIndexFrom, scopeStack)
    nextIndexFrom = skipSpacesParser(str, sexp.indexTo, scopeStack).indexTo
    val rightAt = atSignParser(str, nextIndexFrom, scopeStack)

    val formattedSExp = FormattedSExp(leftAt, sexp, rightAt)
    return ParsingResult(indexFrom, rightAt.indexTo, formattedSExp)
}

val expertStringParser: UniqueParser<ExpertString> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertString> {
    var nextIndexFrom = indexFrom
    val leftQuotation = word("\"")(str, nextIndexFrom, scopeStack)
    nextIndexFrom = leftQuotation.indexTo
    val stringParts = resultsify(*star(disjUnion<StringPart>(
        castParser(formattedStringPartParser),
        castParser(unformattedStringPartParser)
    ))(str, nextIndexFrom, scopeStack).toTypedArray())
    if(stringParts != null) {
        nextIndexFrom = stringParts.found.map { res -> res.indexTo }.max()
    }
    val rightQuotation = word("\"")(str, nextIndexFrom, scopeStack)
    val expertString = ExpertString(leftQuotation, stringParts, rightQuotation)
    return ParsingResult(indexFrom, rightQuotation.indexTo, expertString)
}

val nonSpacedString: UniqueParser<String> = notIn(anyStringParser, spaceParser)
val legalNameParser: UniqueParser<String> =
    concatStringParsers(disjUnion(englishLetterParser, hebrewLetterParser, word("_")),
        stringStar(disjUnion(englishLetterParser, hebrewLetterParser, digitCharParser, word("_"))))

fun arithmeticSignParser(sign: String): UniqueParser<OperatorReservedWord> {
    val rator = signToRator(sign)
    if(!isOperatorReservedWordName(rator)) {
        throw IllegalArgumentException("Not an operator: $rator")
    }
    return disjUnion(pack(word(sign)) { _ -> createReservedWord(rator) as OperatorReservedWord },
        ratorReservedWordParser(rator))
}

val minusSignParser: UniqueParser<OperatorReservedWord> = pack(word("-")) { _ -> createReservedWord(MINUS) as OperatorReservedWord }
val plusSignParser: UniqueParser<OperatorReservedWord> = pack(word("+")) {_ -> createReservedWord(PLUS) as OperatorReservedWord}
val processReservedWordParser = reservedWordParser(PROCESS)
val processReservedWordIs = reservedWordParser(IS)
val plusOperatorParser = arithmeticSignParser("+")
val minusOperatorParser = arithmeticSignParser("-")
val mulOperatorParser = arithmeticSignParser("*")
val divOperatorParser = arithmeticSignParser("/")
val orOperatorParser = ratorReservedWordParser(OR)
val andOperatorParser = ratorReservedWordParser(AND)
val inOperatorParser = ratorReservedWordParser(IN)
val lessThanOperatorParser = arithmeticSignParser("<")
val lessOrEqualOperatorParser = arithmeticSignParser("<=")
val moreThanOperatorParser = arithmeticSignParser(">")
val moreOrEqualOperatorParser = arithmeticSignParser(">=")
val equalsOperatorParser = arithmeticSignParser("=")
val notEqualsOperatorParser = arithmeticSignParser("!=")
val firstInParser = ratorReservedWordParser(FIRST_IN)
val secondInParser = ratorReservedWordParser(SECOND_IN)
val makeEmptyParser = ratorReservedWordParser(MAKE_EMPTY)
val notParser = ratorReservedWordParser(NOT)
val isEmptyParser = ratorReservedWordParser(IS_EMPTY)
val isntEmptyParser = ratorReservedWordParser(NOT_EMPTY)
val doParser = reservedWordParser(DO)
val ifWordParser = reservedWordParser(IF)
val thenWordParser = reservedWordParser(THEN)
val elifWordParser = reservedWordParser(ELIF)
val elseWordParser = reservedWordParser(ELSE)
val endIfWordParser = reservedWordParser(END_IF)
val forEachWordParser = reservedWordParser(FOREACH)
val fromWordParser = reservedWordParser(FROM)
val endLoopWordParser = reservedWordParser(END_LOOP)
val breakProcessWordParser = reservedWordParser(BREAK_PROCESS)
val breakLoopWordParser = reservedWordParser(BREAK_LOOP)
val addWordParser = ratorReservedWordParser(ADD)
val toWordParser = reservedWordParser(TO)
val removeWordParser = ratorReservedWordParser(REMOVE)
val lengthWordParser = ratorReservedWordParser(LENGTH)
val inputWordParser = reservedWordParser(INPUT)
val outputWordParser = reservedWordParser(OUTPUT)
val inputOutputWordParser = reservedWordParser(INPUT_OUTPUT)
val startProcessWordParser = reservedWordParser(START_PROCESS)
val endProcessWordParser = reservedWordParser(END_PROCESS)
val ofReservedWordParser = reservedWordParser(OF)
val anyReservedWordParser = disjUnion(
    ofReservedWordParser, castParser(minusSignParser),
    castParser(plusSignParser), processReservedWordParser, processReservedWordIs,
    castParser(plusOperatorParser), castParser(minusOperatorParser),
    castParser(mulOperatorParser), castParser(divOperatorParser),
    castParser(orOperatorParser), castParser(andOperatorParser),
    castParser(inOperatorParser), castParser(lessThanOperatorParser),
    castParser(lessOrEqualOperatorParser), castParser(moreThanOperatorParser),
    castParser(moreOrEqualOperatorParser), castParser(equalsOperatorParser),
    castParser(notEqualsOperatorParser), castParser(firstInParser),
    castParser(secondInParser), castParser(makeEmptyParser),
    castParser(notParser), castParser(isEmptyParser),
    castParser(isntEmptyParser), doParser, ifWordParser, thenWordParser,
        elifWordParser, elseWordParser, endIfWordParser, forEachWordParser, fromWordParser, endLoopWordParser,
        breakProcessWordParser, breakLoopWordParser, castParser(addWordParser), toWordParser,
    castParser(removeWordParser), castParser(lengthWordParser),
        inputOutputWordParser, inputWordParser, outputWordParser, startProcessWordParser, endProcessWordParser
    )

val processNameParser: UniqueParser<ProcessName> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ProcessName> {
    val parser = demandSpaces<Any>(castParser(processReservedWordParser), castParser(unreservedLegalNameParser))
    val result = parser(str, indexFrom, scopeStack)
    val processName = ProcessName(castResult(result[0]), castResult(result[1]))
    return ParsingResult(indexFrom, result[1].indexTo, processName)
}

val sexpInBracketsParser: UniqueParser<ExpertSExpressionInBrackets> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertSExpressionInBrackets> {
        val parser = allowSpaces<Any>(castParser(word("(")), castParser(sexpParser), castParser(word(")")))
        val result = parser(str, indexFrom, scopeStack)
        val expInBrackets = ExpertSExpressionInBrackets(castResult(result[0]),
            castResult(result[1]), castResult(result[2]))

        return ParsingResult(indexFrom, result[2].indexTo, expInBrackets)
}

val unreservedLegalNameParser: UniqueParser<String> =
    specialCharTerminated(diff(legalNameParser, anyReservedWordParser))

val varRefParser: UniqueParser<VarRef> =
    pack(unreservedLegalNameParser) { name: String -> VarRef(name) }


val varAssignmentParser: UniqueParser<VarAssignment> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<VarAssignment>  {
        val parser = demandSpaces<Any>(castParser(unreservedLegalNameParser), castParser(processReservedWordIs),
            castParser(sexpParser))
        val result = parser(str, indexFrom, scopeStack)
        val assignment = VarAssignment(castResult(result[0]), castResult(result[1]),
            castResult(result[2]))
        return ParsingResult(indexFrom, result[2].indexTo, assignment)
}

fun ratorPriority(arithmeticRator: String): Int {
    return when(arithmeticRator) {
        OR -> 1
        AND -> 2
        LESS_THAN, LESS_OR_EQUAL, MORE_THAN,
        MORE_OR_EQUAL, EQUALS, NOT_EQUALS, IN -> 3
        PLUS, MINUS -> 4
        MUL, DIV -> 5
        else -> 100000
    }
}

val infixOperatorParser = disjUnion(plusOperatorParser, minusOperatorParser, mulOperatorParser, divOperatorParser,
    orOperatorParser, andOperatorParser, inOperatorParser, lessThanOperatorParser, lessOrEqualOperatorParser,
    moreThanOperatorParser, moreOrEqualOperatorParser, equalsOperatorParser, notEqualsOperatorParser)

val infixParserToList: ListParser<Any> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ArrayList<ParsingResult<Any>> {
    var nextIndexFrom = indexFrom
    val infixFirstPairParserToList = allowSpaces<Any>(castParser(nonInfixSExpParser),
        castParser(infixOperatorParser), castParser(nonInfixSExpParser))
    val infixRestParserToList: ListParser<Any> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<Any>> {
        val results = ArrayList<ParsingResult<Any>>()
        var nextIndexFrom = indexFrom
        try {
            while(true) {
                val rator = infixOperatorParser(str, nextIndexFrom, scopeStack)
                nextIndexFrom = rator.indexTo
                nextIndexFrom = skipSpacesParser(str, nextIndexFrom, scopeStack).indexTo
                val param = nonInfixSExpParser(str, nextIndexFrom, scopeStack)
                nextIndexFrom = param.indexTo
                nextIndexFrom = skipSpacesParser(str, nextIndexFrom, scopeStack).indexTo
                results.add(castResult(rator))
                results.add(castResult(param))
            }
        } catch (_: NoMatchError) { }
        return results
    }
    val first = infixFirstPairParserToList(str, nextIndexFrom, scopeStack)
    nextIndexFrom = first.map { res -> res.indexTo }.max()

    nextIndexFrom = skipSpacesParser(str, nextIndexFrom, scopeStack).indexTo

    val rest = infixRestParserToList(str, nextIndexFrom, scopeStack)

    val res = ArrayList<ParsingResult<Any>>()
    res.addAll(first)
    res.addAll(rest)

    return res
}

val infixApplicationParser: UniqueParser<PrimitiveApplication> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<PrimitiveApplication> {
    val expsList = ArrayList(infixParserToList(str, indexFrom, scopeStack))
    val prioritiesList = (unique(expsList.filter { e -> e.found is ReservedWord })).map { rw -> ratorPriority((rw.found as ReservedWord).value) }.sorted()
    val prioritiesStack = Stack<Int>()
    for(priority in prioritiesList) {
        prioritiesStack.push(priority)
    }
    while(!prioritiesStack.empty()) {
        val highestPriority = prioritiesStack.pop()
        val priorityPred =
            { exp: ParsingResult<Any> -> (exp.found is ReservedWord) && ratorPriority(exp.found.value) == highestPriority }
        var nextRatorIndex = expsList.indexOfFirst(priorityPred)
        while (nextRatorIndex != -1) {
            val exp1 = castResult<Any, SExp>(expsList[nextRatorIndex - 1])
            val rand = castResult<Any, OperatorReservedWord>(expsList[nextRatorIndex])
            val exp2 = castResult<Any, SExp>(expsList[nextRatorIndex + 1])
            val results = resultsify(exp1, exp2)
            val appExpressionRes = ParsingResult<PrimitiveApplication>(
                exp1.indexFrom, exp2.indexTo,
                PrimitiveApplication(rand, results, null)
            )

            expsList[nextRatorIndex - 1] = castResult(appExpressionRes)
            expsList.removeAt(nextRatorIndex)
            expsList.removeAt(nextRatorIndex)

            nextRatorIndex =  expsList.indexOfFirst(priorityPred)
        }
    }
    if(expsList[0].found !is PrimitiveApplication){
        throw UnknownError("${expsList[0].found} is not an infix application!")
    }

    return castResult(expsList[0])
}

val signAppParser: UniqueParser<PrimitiveApplication> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<PrimitiveApplication> {
    var isNumber = false
    try {
        numberParser(str, indexFrom, scopeStack)
        isNumber = true
    } catch (_: NoMatchError) {}
    if(isNumber) {
        throw NoMatchError()
    }

    val exps = allowSpaces<Any>(castParser(disjUnion(minusSignParser, plusSignParser)), castParser(sexpParser))(str, indexFrom, scopeStack)

    val rator = castResult<Any, OperatorReservedWord>(exps[0])
    val rand = castResult<Any, SExp>(exps[1])

    return ParsingResult(rator.indexFrom, rand.indexTo,
        PrimitiveApplication(rator, resultsify(rand), contextWord = null))
}

fun nonSignAppParserFun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<PrimitiveApplication> {
    var nextIndexFrom = indexFrom
    val rator = prefixOperatorParser(str, nextIndexFrom, scopeStack)
    if(!isOperatorReservedWordName(rator.found.value)) {
        throw NoMatchError()
    }

    nextIndexFrom = skipSpacesParser(str, rator.indexTo, scopeStack).indexTo
    val rands = resultsify(disjUnion(sexpParser, castParser(prefixNonSignAppParser))(str, nextIndexFrom, scopeStack))
    val app = PrimitiveApplication(rator, rands)
    return ParsingResult(indexFrom, rands!!.indexTo, app)
}

val prefixNonSignAppParser: UniqueParser<PrimitiveApplication> = {str, indexFrom, scopeStack -> nonSignAppParserFun(str, indexFrom, scopeStack) }

val prefixPrimitiveAppParser = disjUnion(signAppParser, prefixNonSignAppParser)

val prefixOperatorParser = disjUnion(minusSignParser, plusSignParser, firstInParser, secondInParser,
    makeEmptyParser, notParser, lengthWordParser)

val ofAndSpaceReservedWordParser = concatAndPack(ofReservedWordParser, demandSpacesParser) { of: ReservedWord, _ -> of }

val nonPrimitiveParamWithOfParser: UniqueParser<ParameterContent> =
    fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ParameterContent> {
        val ofWord: ParsingResult<ReservedWord> = ofAndSpaceReservedWordParser(str, indexFrom, scopeStack)
        val sExp: ParsingResult<SExp> = nonApplicativeSExpParser(str, ofWord.indexTo, scopeStack)
        val param = ParameterContent(sExp, ofWord)

        return ParsingResult(indexFrom, sExp.indexTo, param)
}

val nonPrimitiveParamWithoutOfParser: UniqueParser<ParameterContent> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ParameterContent> {
        val result = disjUnion<SExp>(castParser(expertStringParser), castParser(numberParser))(str, indexFrom, scopeStack)
        return ParsingResult(result.indexFrom, result.indexTo, ParameterContent(result))
}

val nonPrimitiveParamParser = disjUnion(nonPrimitiveParamWithOfParser, nonPrimitiveParamWithoutOfParser)

val optionalNonPrimitiveParamsParser = spacedStar(nonPrimitiveParamParser)
val nonPrimitiveParamsParser = spacedPlus(nonPrimitiveParamParser)

val libraryCallParser: UniqueParser<LibraryApplication> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<LibraryApplication> {
        var nextIndexFrom = indexFrom
        val functionName = varRefParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, functionName.indexTo, scopeStack).indexTo
        val params = resultsify(*nonPrimitiveParamsParser(str, nextIndexFrom, scopeStack).toTypedArray())
        val rands = params?.found?.map { func -> func.found.param } ?: ArrayList()
        val ofWords = params?.found?.map { func -> func.found.ofWord }?.filterNotNull() ?: ArrayList()

        val fixedRands = resultsify(*rands.toTypedArray())
        val fixedOfWords = resultsify(*ofWords.toTypedArray())

        val indexTo = params?.indexTo ?: functionName.indexTo
        val libraryCall = LibraryApplication(functionName, fixedRands, fixedOfWords)
        return ParsingResult(indexFrom, indexTo, libraryCall)
}


val postfixOperatorParser = disjUnion(isEmptyParser, isntEmptyParser)
val postfixAppParser: UniqueParser<PrimitiveApplication> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<PrimitiveApplication> {
        var nextIndexFrom = indexFrom
        val sExp = nonApplicativeSExpParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, sExp.indexTo, scopeStack).indexTo
        val rator = postfixOperatorParser(str, nextIndexFrom, scopeStack)
        if (!isOperatorReservedWordName(rator.found.value)) {
            throw NoMatchError()
        }
        val app = PrimitiveApplication(rator, resultsify(sExp))
        return ParsingResult(indexFrom, rator.indexTo, app)
}

val nonApplicativeSExpParser: UniqueParser<SExp> = disjUnion(castParser(sexpInBracketsParser),
    castParser(expertStringParser), castParser(numberParser), castParser(varRefParser))

val nonInfixSExpParser: UniqueParser<SExp> = disjUnion(castParser(postfixAppParser),
    castParser(sexpInBracketsParser), castParser(prefixPrimitiveAppParser), castParser(libraryCallParser),
    castParser(expertStringParser), castParser(numberParser), castParser(varRefParser))

val sexpParser: UniqueParser<SExp> = disjUnion(castParser(infixApplicationParser),
    castParser(postfixAppParser), castParser(sexpInBracketsParser), castParser(prefixPrimitiveAppParser),
    castParser(libraryCallParser), castParser(expertStringParser), castParser(numberParser), castParser(varRefParser))

val processCallParser: UniqueParser<ExpertProcessCall> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertProcessCall> {
    var nextIndexFrom = indexFrom
    val doAndProcessName = demandSpaces<Exp>(castParser(doParser), castParser(varRefParser))(str, nextIndexFrom, scopeStack)
    val doWord: ParsingResult<ReservedWord> = castResult(doAndProcessName[0])
    val processName: ParsingResult<VarRef> = castResult(doAndProcessName[1])

    nextIndexFrom = spaceParser(str, processName.indexTo, scopeStack).indexTo
    val params = optionalNonPrimitiveParamsParser(str, nextIndexFrom, scopeStack)
    val rands = params.map { func -> func.found.param }
    val ofWords = params.mapNotNull { func -> func.found.ofWord }

    val indexTo =  if(params.isEmpty()) processName.indexTo else params.last().indexTo
    val processCall = ExpertProcessCall(processName, doWord, resultsify(*ofWords.toTypedArray()), resultsify(*rands.toTypedArray()))

    return ParsingResult(indexFrom, indexTo, processCall)
}

val ifThenExpressionParser: UniqueParser<ExpertIfThenExpression> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertIfThenExpression> {
        var nextIndexFrom = indexFrom
        val results = demandSpaces<Any?>(castParser(ifWordParser), castParser(sexpParser),
            castParser(optional(thenWordParser)))(str,nextIndexFrom, scopeStack)
        val ifWord: ParsingResult<ReservedWord> = castResult(results[0])
        val test: ParsingResult<SExp> = castResult(results[1])
        val thenWord: ParsingResult<ReservedWord?> = castResult(results[2])
        val fixedThen: ParsingResult<ReservedWord>? = if(thenWord.found == null) null else castResult(thenWord)

        val indexBeforeSpaces = thenWord.indexTo
        nextIndexFrom = demandSpacesParser(str, thenWord.indexTo, scopeStack).indexTo
        val finishBodyExp = optional(disjUnion(endIfWordParser, elifWordParser, elseWordParser))(str, nextIndexFrom, scopeStack)
        if(finishBodyExp.found != null) {
            val emptyBody = ExpertScope(resultsify())
            val ifExp = ExpertIfThenExpression(ifWord, test, ParsingResult(indexBeforeSpaces, indexBeforeSpaces, emptyBody), fixedThen)
            return ParsingResult(indexFrom, indexBeforeSpaces, ifExp)
        }
        val body = getScopeParser()(str, nextIndexFrom, scopeStack)
        val ifExp = ExpertIfThenExpression(ifWord, test, body, fixedThen)

        return ParsingResult(indexFrom, body.indexTo, ifExp)
}

val elifExpressionParser: UniqueParser<ExpertElifExpression> =
    fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertElifExpression> {
        var nextIndexFrom = indexFrom
        val results = demandSpaces<Any?>(castParser(elifWordParser), castParser(sexpParser),
            castParser(optional(thenWordParser)))(str,nextIndexFrom, scopeStack)
        val elifWord: ParsingResult<ReservedWord> = castResult(results[0])
        val test: ParsingResult<SExp> = castResult(results[1])
        val thenWord: ParsingResult<ReservedWord?> = castResult(results[2])
        val fixedThen: ParsingResult<ReservedWord>? = if(thenWord.found == null) null else castResult(thenWord)

        val indexBeforeSpaces = thenWord.indexTo
        nextIndexFrom = demandSpacesParser(str, thenWord.indexTo, scopeStack).indexTo
        val finishBodyExp = optional(disjUnion(endIfWordParser, elifWordParser, elseWordParser))(str, nextIndexFrom, scopeStack)
        if(finishBodyExp.found != null) {
            val emptyBody = ExpertScope(resultsify())
            val elifExp = ExpertElifExpression(elifWord, test, ParsingResult(indexBeforeSpaces, indexBeforeSpaces, emptyBody), fixedThen)
            return ParsingResult(indexFrom, indexBeforeSpaces, elifExp)
        }
        val body = getScopeParser()(str, nextIndexFrom, scopeStack)
        val elifExp = ExpertElifExpression(elifWord, test, body, fixedThen)

        return ParsingResult(indexFrom, body.indexTo, elifExp)
}

val elseExpressionParser: UniqueParser<ExpertElseExpression> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertElseExpression> {
    var nextIndexFrom = indexFrom
    val elseWord = elseWordParser(str, nextIndexFrom, scopeStack)

    val indexBeforeSpaces = elseWord.indexTo
    nextIndexFrom = demandSpacesParser(str, elseWord.indexTo, scopeStack).indexTo
    val finishBodyExp = optional(endIfWordParser)(str, nextIndexFrom, scopeStack)
    if(finishBodyExp.found != null) {
        val emptyBody = ExpertScope(resultsify())
        val elseExp = ExpertElseExpression(elseWord, ParsingResult(indexBeforeSpaces, indexBeforeSpaces, emptyBody))
        return ParsingResult(indexFrom, indexBeforeSpaces, elseExp)
    }
    val body = getScopeParser()(str, nextIndexFrom, scopeStack)
    val elseExp = ExpertElseExpression(elseWord, body)

    return ParsingResult(indexFrom, body.indexTo, elseExp)
}
val fullIfExpressionParser: UniqueParser<ExpertFullIfExpression> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertFullIfExpression> {
    val internalFullIfExpressionParser: UniqueParser<ExpertFullIfExpression> = fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertFullIfExpression> {
        var nextIndexFrom = indexFrom

        val ifExp = ifThenExpressionParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = skipSpacesParser(str, ifExp.indexTo, scopeStack).indexTo

        val elifs = spacedStar(elifExpressionParser)(str, nextIndexFrom, scopeStack)
        nextIndexFrom = if (elifs.isEmpty()) nextIndexFrom else skipSpacesParser(str, elifs.last().indexTo, scopeStack).indexTo

        val elseExp = optional(elseExpressionParser)(str, nextIndexFrom, scopeStack)
        if(elseExp.found != null) {
            nextIndexFrom = skipSpacesParser(str, elseExp.indexTo, scopeStack).indexTo
        }

        val endIfExp = optional(endIfWordParser)(str, nextIndexFrom, scopeStack)
        val fixedEndIfExp: ParsingResult<ReservedWord>? = if(endIfExp.found == null) null else castResult(endIfExp)
        val indexTo = endIfExp.indexTo
        val fixedElse: ParsingResult<ExpertElseExpression>? =  if(elseExp.found == null) null else castResult(elseExp)
        val fullIfExp = ExpertFullIfExpression(ifExp, resultsify(*elifs.toTypedArray()), fixedElse, fixedEndIfExp)

        return ParsingResult(indexFrom, indexTo, fullIfExp)
    }

    scopeStack.enterIf()
    try {
        val res = internalFullIfExpressionParser(str, indexFrom, scopeStack)
        scopeStack.exitScope()
        return res
    } catch (e: Exception) {
        scopeStack.exitScope()
        throw e
    }
}

val listAddOrRemoveParser = {ratorParser: UniqueParser<ReservedWord>, contextWordParser: UniqueParser<ReservedWord> ->
    fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<PrimitiveApplication> {
        var nextIndexFrom = indexFrom
        val ratorWord = castParser<ReservedWord, OperatorReservedWord>(ratorParser)(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, ratorWord.indexTo, scopeStack).indexTo
        val listVal = nonApplicativeSExpParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, listVal.indexTo, scopeStack).indexTo
        val contextWord = contextWordParser (str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, contextWord.indexTo, scopeStack).indexTo
        val listName = varRefParser (str, nextIndexFrom, scopeStack)

        val app = PrimitiveApplication(ratorWord, resultsify(listVal, castResult(listName)), castResult(contextWord))
        return ParsingResult(indexFrom, listName.indexTo, app)
    }
}

val addExpressionParser: UniqueParser<PrimitiveApplication> = listAddOrRemoveParser(castParser(addWordParser), toWordParser)
val removeExpressionParser: UniqueParser<PrimitiveApplication> = listAddOrRemoveParser(castParser(removeWordParser), fromWordParser)
val listSpecialOperatorParser: UniqueParser<PrimitiveApplication> = disjUnion(addExpressionParser, removeExpressionParser)


val forEachParser: UniqueParser<ExpertForEachExp> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertForEachExp> {
    val internalForEachParser: UniqueParser<ExpertForEachExp> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<ExpertForEachExp> {
        var nextIndexFrom = indexFrom
        val foreachWord = forEachWordParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, foreachWord.indexTo, scopeStack).indexTo
        val loopVar = varRefParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, loopVar.indexTo, scopeStack).indexTo
        val fromWord = fromWordParser(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, fromWord.indexTo, scopeStack).indexTo
        val listVar = varRefParser(str, nextIndexFrom, scopeStack)
        val indexBeforeSpaces = listVar.indexTo
        nextIndexFrom = demandSpacesParser(str, listVar.indexTo, scopeStack).indexTo

        val earlyEndLoop = optional(endLoopWordParser)(str, nextIndexFrom, scopeStack)
        if(earlyEndLoop.found != null) {
            val emptyBody = ParsingResult(indexBeforeSpaces, indexBeforeSpaces, ExpertScope(resultsify()))
            val foreachExp = ExpertForEachExp(foreachWord, loopVar, fromWord, listVar, emptyBody, castResult(earlyEndLoop))
            return ParsingResult(indexFrom, earlyEndLoop.indexTo, foreachExp)
        }

        val body = getScopeParser()(str, nextIndexFrom, scopeStack)
        nextIndexFrom = demandSpacesParser(str, body.indexTo, scopeStack).indexTo
        val endLoopWord = optional(endLoopWordParser)(str, nextIndexFrom, scopeStack)
        val fixedEndLoopWord: ParsingResult<ReservedWord>? = if(endLoopWord.found == null) null else castResult(endLoopWord)

        val indexTo = endLoopWord.indexTo
        val foreachExp = ExpertForEachExp(foreachWord, loopVar, fromWord, listVar, body, fixedEndLoopWord)

        return ParsingResult(indexFrom, indexTo, foreachExp)
    }

    scopeStack.enterLoop()
    try {
        val res = internalForEachParser(str, indexFrom, scopeStack)
        scopeStack.exitScope()
        return res
    } catch (e: Exception) {
        scopeStack.exitScope()
        throw e
    }
}

val unrecognizedExpressionParser: UniqueParser<UnrecognizedExpression> = fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<UnrecognizedExpression> {
    var res = stringStar(complementChar("\n"))(str, indexFrom, scopeStack)
    if(res.indexTo < str.length) {
        res = ParsingResult(res.indexFrom, res.indexTo+1, res.found)
    }
    return ParsingResult<UnrecognizedExpression>(res.indexFrom, res.indexTo, UnrecognizedExpression())
}


val controlFlowWordParser = disjUnion<OtherReservedWord>(castParser(breakProcessWordParser),
    castParser(breakLoopWordParser), castParser(endLoopWordParser), castParser(endIfWordParser))

val processScopeExpressionParser: UniqueParser<ScopeExp> = {str, indexFrom, scopeStack ->
    strictDiff(disjUnion<ScopeExp>(castParser(varAssignmentParser), castParser(forEachParser),
        castParser(fullIfExpressionParser), castParser(processCallParser), castParser(libraryCallParser),
        castParser(prefixNonSignAppParser), castParser(listSpecialOperatorParser), castParser(controlFlowWordParser),
        castParser(varRefParser), castParser(unrecognizedExpressionParser)), endProcessWordParser)(str, indexFrom, scopeStack)
}


val loopScopeExpressionParser = strictDiff(processScopeExpressionParser, endLoopWordParser)
val ifScopeExpressionParser = strictDiff(processScopeExpressionParser,
    disjUnion(endIfWordParser, elseWordParser, elifWordParser, thenWordParser))
val ifAndLoopScopeExpressionParser = strictDiff(processScopeExpressionParser,
    disjUnion(endIfWordParser, elseWordParser, elifWordParser, thenWordParser,endLoopWordParser))

fun getScopeExpParser(scopeStack: ScopeStack): UniqueParser<ScopeExp> {
    if(scopeStack.inIf() && scopeStack.inLoop()) {
        return ifAndLoopScopeExpressionParser;
    } else if(scopeStack.inIf()) {
        return ifScopeExpressionParser;
    } else if(scopeStack.inLoop()) {
        return loopScopeExpressionParser;
    }
    return processScopeExpressionParser;
}


fun getScopeParser(): UniqueParser<ExpertScope> {
    return {str, indexFrom, scopeStack ->
        val results = resultsify(*spacedStar(getScopeExpParser(scopeStack))(str, indexFrom, scopeStack).toTypedArray())
        val scope = ExpertScope(results)
        if(results == null) {
            throw NoMatchError()
        }
        ParsingResult(results.indexFrom, results.indexTo, scope)
    }
}

val documentationCommentParser: UniqueParser<String> = {str, indexFrom, scopeStack: ScopeStack ->
    val prefix = concatMany(word("{"), stringStar(word("!")), stringStar(word("*")), stringStar(notNoneSpacesParser))
    val content = stringStar(complementChar("}"))
    val postfix = word("}")


    val prefixAndContentParser = concatAndPack(prefix, content) { _, content -> content }
    val fullParser = concatAndPack(prefixAndContentParser, postfix) { content, postfix ->
        content.substring(
            0,
            Regex("( )*(\\*)*}").find("$content}")!!.range.first
        )
    }

    fullParser(str, indexFrom, scopeStack)
}

fun <T> specialCharTerminated(p: UniqueParser<T>): UniqueParser<T> {
    return {str, indexFrom, stack ->
        val res = p(str, indexFrom, stack)
        if(res.indexTo != str.length) {
            disjUnion(demandSpacesParser, unitify(word(")")), unitify(word("\"")), unitify(word("@")))(str, res.indexTo, stack)
        }

        res
    }
}

val documentationParser: UniqueParser<String> = {str, indexFrom, scopeStack ->
    var indexTo = -1
    val results = ArrayList<String>()
    val optionalParser = optional(documentationCommentParser)
    var nextIndexFrom = str.indexOf("{!", indexFrom)
    while(nextIndexFrom != -1) {
        val res = optionalParser(str, nextIndexFrom, scopeStack)
        if(res.found == null) {
            nextIndexFrom++
        } else {
            results.add(res.found)
            nextIndexFrom = res.indexTo
            indexTo = nextIndexFrom
        }

        nextIndexFrom = str.indexOf("{!", nextIndexFrom)
    }

    ParsingResult(indexFrom, indexTo, results.joinToString("\n"))
}
val allCommentsParser: ListParser<ExpertComment> = {str, indexFrom, scopeStack ->
    val comments = ArrayList<ParsingResult<ExpertComment>>()
    val optionalParser = optional(commentsParser)

    var nextIndexFrom = str.substring(indexFrom).indexOfFirst { c -> c == '{' } + indexFrom
    while(nextIndexFrom != -1) {
        val res = optionalParser(str, nextIndexFrom, scopeStack)
        if(res.found == null) {
            nextIndexFrom++
        } else {
            comments.add(castResult(res))
            nextIndexFrom = res.indexTo
        }
        val nextRelative = str.substring(nextIndexFrom).indexOfFirst { c -> c == '{' }
        if(nextRelative == -1) {
            break
        }
        nextIndexFrom += nextRelative
    }

    comments
}

val typeHintParser: UniqueParser<TypeHint> = {str, indexFrom, scopeStack ->
    val leftBracketParser = word("(")
    val rightBracketParser = word(")")
    val p = allowSpaces(leftBracketParser, unreservedLegalNameParser,
        rightBracketParser)
    val results = p(str, indexFrom, scopeStack)
    val leftBracket: ParsingResult<String> = results[0]
    val name: ParsingResult<String> = results[1]
    val rightBracket: ParsingResult<String> = results[2]
    val typeHint = TypeHint(leftBracket, name, rightBracket)

    ParsingResult(indexFrom, rightBracket.indexTo, typeHint)
}

val headerVarParser: UniqueParser<HeaderVar> =  {str, indexFrom, scopeStack ->
    val results = allowSpaces<Any?>(castParser(unreservedLegalNameParser), castParser(optional(typeHintParser)))(str, indexFrom, scopeStack)
    val name: ParsingResult<String> = castResult(results[0])
    val typeHint: ParsingResult<TypeHint?> = castResult(results[1])
    val indexTo =  if (typeHint.found == null) name.indexTo else typeHint.indexTo
    val header = if(typeHint.found == null) HeaderVar(name) else HeaderVar(name, castResult(typeHint))

    ParsingResult(indexFrom, indexTo, header)
}

val varTypeParser = disjUnion(inputOutputWordParser, inputWordParser, outputWordParser)
val headerVariablesParser: UniqueParser<HeaderVariables> = fun(str: String, indexFrom: Int, scopeStack): ParsingResult<HeaderVariables> {
    var nextIndexFrom = indexFrom
    val type = varTypeParser(str, indexFrom, scopeStack)
    nextIndexFrom = demandSpacesParser(str, type.indexTo, scopeStack).indexTo
    val results = resultsify(*spacedStar(headerVarParser)(str, nextIndexFrom, scopeStack).toTypedArray())

    val variables = HeaderVariables(type, results)
    val indexTo = results?.indexTo ?: type.indexTo
    return  ParsingResult(indexFrom, indexTo, variables)
}

val processHeaderParser: UniqueParser<ProcessHeader> = {str, indexFrom, scopeStack ->
    val unrecognized = ArrayList<ParsingResult<UnrecognizedExpression>>()
    var nextIndexFrom = indexFrom
    val unrecognizedParser = diff(unrecognizedExpressionParser, startProcessWordParser)

    val processNameOrUnrecognizedParser = disjUnion<Any>(castParser(processNameParser), castParser(unrecognizedParser))
    nextIndexFrom = skipSpacesParser(str, nextIndexFrom, scopeStack).indexTo
    var potentialName = processNameOrUnrecognizedParser(str, nextIndexFrom, scopeStack)
    while (potentialName.found is UnrecognizedExpression) {
        unrecognized.add(castResult(potentialName))
        if(nextIndexFrom == potentialName.indexTo) {
            throw ParsingError()
        }
        nextIndexFrom = skipSpacesParser(str, potentialName.indexTo, scopeStack).indexTo
        potentialName = processNameOrUnrecognizedParser(str, nextIndexFrom, scopeStack)
    }
    val name: ParsingResult<ProcessName> = castResult(potentialName)
    nextIndexFrom = skipSpacesParser(str, potentialName.indexTo, scopeStack).indexTo

    val headerVariablesOrUnrecognizedParser = strictDiff(disjUnion<Any>(castParser(headerVariablesParser),
        castParser(unrecognizedParser)), startProcessWordParser)
    val variables = ArrayList<ParsingResult<HeaderVariables>>()


    val results = spacedStar(headerVariablesOrUnrecognizedParser)(str, nextIndexFrom, scopeStack)
    results.forEach { result ->
        if(result.found is UnrecognizedExpression) {
            unrecognized.add(castResult(result))
        } else if (variables.indexOfFirst { value -> value.found.type == castResult<Any, HeaderVariables>(result).found.type } != -1) {
            val unrecognizedRes = packResult(result) { _ -> UnrecognizedExpression() }
            unrecognized.add(unrecognizedRes)
        } else {
            variables.add(castResult(result))
        }
    }

    val inputs = variables.firstOrNull { res -> res.found.type.found.value == "INPUT" }
    val outputs = variables.firstOrNull { res -> res.found.type.found.value == "OUTPUT" }
    val inputsOutputs = variables.firstOrNull { res -> res.found.type.found.value == "INPUT OUTPUT" }

    val indexTo = if(results.isEmpty()) nextIndexFrom else results.maxOfOrNull { res -> res.indexTo }!!
    val header = ProcessHeader(name, inputs, outputs, inputsOutputs, resultsify(*unrecognized.toTypedArray()))

    ParsingResult(indexFrom, indexTo, header)
}

val processParser: UniqueParser<ParsedProcess> = {str, indexFrom, scopeStack ->

    val results = allowSpaces<Any?>(castParser(processHeaderParser), castParser(optional(startProcessWordParser)),
        castParser(getScopeParser()), castParser(optional(endProcessWordParser)))(str, indexFrom, scopeStack)


    val header: ParsingResult<ProcessHeader> = castResult(results[0])
    val startProcess: ParsingResult<ReservedWord?> = castResult(results[1])
    val body: ParsingResult<ExpertScope> = castResult(results[2])
    val endProcess: ParsingResult<ReservedWord?> = castResult(results[3])
    val fixedStart: ParsingResult<ReservedWord>? = if(startProcess.found == null) null else castResult(startProcess)
    val fixedEnd: ParsingResult<ReservedWord>? = if(endProcess.found == null) null else castResult(endProcess)

    val process = ParsedProcess(header, fixedStart, body, fixedEnd)
    ParsingResult(indexFrom, endProcess.indexTo, process)
}

fun parseProcess(str: String): ParsingResult<ParsedProcess> {
    return processParser(str, 0, ScopeStack())
}