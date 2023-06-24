package com.parser

import kotlin.reflect.typeOf

fun <T1, T2, T3> concatMethods(f1: (T1) -> T2, f2: (T2) -> T3): (T1) -> T3 {

    return {t1: T1 -> f2(f1(t1))}
}

fun <T> resultsify (vararg results: ParsingResult<T>): Results<T>? {
    val indexFrom = results.minOfOrNull { res -> res.indexFrom }
    val indexTo = results.maxOfOrNull { res -> res.indexTo }
    if(indexFrom == null || indexTo == null) {
        return null
    }
    return ParsingResult(indexFrom, indexTo, results.asList())
}

fun <T> unresultsify(results: Results<T>): List<T> {
    return results.found.map { res -> res.found }
}

fun <T> unlistify  (parser: ListParser<T>): UniqueParser<List<T>> {
    return {str, indexFrom, scopeStack ->
        val results = parser(str, indexFrom, scopeStack)
        val indexTo: Int = if(results.isEmpty()) indexFrom else results.map { res -> res.indexTo }.max()
        ParsingResult(indexFrom, indexTo, results.map { res -> res.found })
    }
}


fun <T1, T2> pack (parser: UniqueParser<T1>, func: (T1) -> T2): UniqueParser<T2> {
    return {str, indexFrom, scopeStack ->
        val res = parser(str, indexFrom, scopeStack)
        ParsingResult(res.indexFrom, res.indexTo, func(res.found))
    }
}
fun <T1, T2> packResult(res: ParsingResult<T1>, func: (T1) -> T2): ParsingResult<T2> {
    return ParsingResult(res.indexFrom, res.indexTo, func(res.found))
}

fun <T1, T2> concat(p1: UniqueParser<T1>, p2: UniqueParser<T2>): UniqueParser<Pair<T1, T2>> {
    return {str, indexFrom, scopeStack ->
        val res1 = p1(str, indexFrom, scopeStack)
        val res2 = p2(str, res1.indexTo, scopeStack)
        ParsingResult(res1.indexFrom, res2.indexTo, Pair(res1.found, res2.found))
    }
}

fun <T1, T2, T3> concatAndPack(p1: UniqueParser<T1>, p2: UniqueParser<T2>, func: (T1, T2) -> T3): UniqueParser<T3> {
    return pack(concat(p1, p2)) { pair -> func(pair.first, pair.second) }
}

fun <T> concatMany(vararg parsers: UniqueParser<T>): UniqueParser<List<T>> {
    return {str, indexFrom, scopeStack ->
        var newIndexFrom = indexFrom
        val results = ArrayList<T>()
        parsers.forEach { p ->
            val res = p(str, newIndexFrom, scopeStack)
            newIndexFrom = res.indexTo
            results += res.found
        }
        ParsingResult(indexFrom, newIndexFrom, results)
    }
}

fun <T> concatList(vararg parsers: UniqueParser<T>): ListParser<T> {
    return {str, indexFrom, scopeStack ->
        var newIndexFrom = indexFrom
        val results = ArrayList<ParsingResult<T>>()
        parsers.forEach { p ->
            val res = p(str, newIndexFrom, scopeStack)
            newIndexFrom = res.indexTo
            results += res
        }
        results
    }
}

fun <T1, T2> concatAndPackList(parsers: List<UniqueParser<T1>>, func: (T1) -> T2): ListParser<T2> {
    return {str, indexFrom, scopeStack ->
        var newIndexFrom = indexFrom
        val results = ArrayList<ParsingResult<T1>>()
        parsers.forEach { p ->
            val res = p(str, newIndexFrom, scopeStack)
            newIndexFrom = res.indexTo
            results += res
        }

        results.map{res -> ParsingResult(res.indexFrom, res.indexTo, func(res.found))}
    }
}

fun <T> reduce(vararg parsers: UniqueParser<T>, reducer: (T, T) -> T): UniqueParser<T> {
    return parsers.reduce { acc: UniqueParser<T>, currentMember: UniqueParser<T> -> concatAndPack(acc, currentMember, reducer) }
}

fun <T1, T2> reduceAndPack(vararg parsers: UniqueParser<T1>, reducer: (T1, T1) -> T1, mapper: (T1) -> T2): UniqueParser<T2> {
    val reduced = reduce(*parsers, reducer=reducer)
    return pack(reduced, mapper)
}

fun concatStringParsers(vararg parsers: UniqueParser<String>): UniqueParser<String> {
    return reduce(*parsers, reducer={s1, s2 -> s1 + s2})
}

fun <T> concatListParsers(vararg parsers: ListParser<T>): ListParser<T> {
    return {str, indexFrom, scopeStack ->
        val results = ArrayList<ParsingResult<T>>()
        var nextIndexFrom = indexFrom
        parsers.forEach { p ->
            val res = p(str, nextIndexFrom, scopeStack)
            if(res.isNotEmpty()) {
                nextIndexFrom = res.last().indexTo
                results.addAll(res)
            }
        }
        results
    }
}
fun <T> concatParsersOfLists(vararg parsers: UniqueParser<List<T>>): UniqueParser<List<T>> {
    return {str, indexFrom, scopeStack ->
        val results = ArrayList<T>()
        var nextIndexFrom = indexFrom
        parsers.forEach { p ->
            val res = p(str, nextIndexFrom, scopeStack)
            results.addAll(res.found)
            nextIndexFrom = res.indexTo
        }
        ParsingResult(indexFrom, nextIndexFrom, results)
    }
}

val anyCharParser: UniqueParser<String> = {str, indexFrom, scopeStack ->
    if(indexFrom >= str.length) {
        throw NoMatchError()
    }
    ParsingResult(indexFrom, indexFrom+1, str.substring(indexFrom, indexFrom+1))
}

val anyStringParser: UniqueParser<String> = stringPlus(anyCharParser)

fun <T1, T2> notIn(goodParser: UniqueParser<T1>, badParser: UniqueParser<T2>): UniqueParser<T1> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T1> {
        val res = goodParser(str, indexFrom, scopeStack)
        var maxI: Int = indexFrom
        for(i in indexFrom until res.indexTo) {
            var shouldBreak = false
            try {
                badParser(str, i, scopeStack)
                shouldBreak = true
            } catch (_: NoMatchError) {
                maxI = i
            }
            if(shouldBreak)
               break
        }

        return goodParser(str.substring(0, maxI), indexFrom, scopeStack)
    }
}


fun <T> makeNoneParser(): UniqueParser<T> {
    return { _, _, _ -> throw NoMatchError() }
}

val nonCharParser = makeNoneParser<String>()

fun <T> disjUnion(vararg parsers: UniqueParser<T>): UniqueParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T> {
        for(parser in parsers) {
            try {
                return parser(str, indexFrom, scopeStack)
            } catch (_: NoMatchError){}
        }
        throw NoMatchError()
    }
}

fun <T> longestUnion(vararg parsers: UniqueParser<T>): UniqueParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T> {
        var maxResult: ParsingResult<T>? = null
        for(parser in parsers) {
            try {
                val result = parser(str, indexFrom, scopeStack)
                if(maxResult == null || maxResult.length() < result.length()) {
                    maxResult = result;
                }

            } catch (_: NoMatchError){}
        }

        if(maxResult == null) {
            throw NoMatchError()
        }

        return maxResult
    }

}

fun <T> makeGeneralEpsilonParser(default: T): UniqueParser<T> {
    return {str, _, _ ->
        if(str != "") {
            throw NoMatchError()
        }
        ParsingResult(0, 0, default)
    }
}

val epsilonParser = makeGeneralEpsilonParser("")

fun <T> star(parser: UniqueParser<T>): ListParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<T>> {
        val results = ArrayList<ParsingResult<T>>()
        var nextIndexFrom = indexFrom
        try {
            while (nextIndexFrom < str.length) {
                val newRes = parser(str, nextIndexFrom, scopeStack)
                if(newRes.indexFrom == newRes.indexTo) {
                    break
                }
                results += newRes
                nextIndexFrom = newRes.indexTo
            }
        } catch (_: NoMatchError) {}

        return results
    }
}

fun <T> listStar(parser: ListParser<T>): ListParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): List<ParsingResult<T>> {
        val results = ArrayList<ParsingResult<T>>()
        var nextIndexFrom = indexFrom
        try {
            while (nextIndexFrom < str.length) {
                val newRes = parser(str, indexFrom, scopeStack)
                val resIndexFrom = newRes.map { res -> res.indexFrom }.minOrNull()
                val resIndexTo = newRes.map { res -> res.indexTo}.maxOrNull()
                if(resIndexFrom == resIndexTo || resIndexTo == null) {
                    break
                }
                results += newRes
                nextIndexFrom = resIndexTo
            }
        } catch (_: NoMatchError) {}

        return results
    }
}

fun stringStar(parser: UniqueParser<String>): UniqueParser<String> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<String> {
        val results = star(parser)(str, indexFrom, scopeStack)
        val s = results.joinToString("") { sRes: ParsingResult<String> -> sRes.found }
        val indexTo = if(results.isEmpty()) indexFrom else results.last().indexTo
        return ParsingResult(indexFrom, indexTo, s)
    }
}

fun <T> plus(parser: UniqueParser<T>): ListParser<T> {
    return {str, indexFrom, scopeStack ->
        val first = parser(str, indexFrom, scopeStack)
        val rest = star(parser)(str, first.indexTo, scopeStack)

        val res = ArrayList<ParsingResult<T>>()
        res.add(first)
        res.addAll(rest)

        res
    }
}

fun stringPlus(parser: UniqueParser<String>): UniqueParser<String> {
    return concatStringParsers(parser, stringStar(parser))
}

fun pow(parser: UniqueParser<String>, pow: Int): UniqueParser<String> {
    var res = epsilonParser
    for(i in 1..pow) {
        res = concatStringParsers(res, parser)
    }
    return res
}

fun word(word: String): UniqueParser<String> {
    if(word == "") {
        return epsilonParser
    }
    return fun(str: String, indexFrom: Int, _): ParsingResult<String> {
        if(indexFrom > str.length - word.length || str.substring(indexFrom, indexFrom + word.length) != word) {
            throw NoMatchError()
        }
        return ParsingResult(indexFrom, indexFrom + word.length, word)
    }
}

fun <T> spaceTerminated(p: UniqueParser<T>): UniqueParser<T> {
    return {str, indexFrom, stack ->
        val res = p(str, indexFrom, stack)
        if(res.indexTo != str.length) {
            spaceParser(str, res.indexTo, stack)
        }

        res
    }
}



fun spaceTerminatedWord(str: String): UniqueParser<String> = spaceTerminated(word(str))


fun complementChar(badChar: String): UniqueParser<String> {
    if(badChar.length != 1) {
        throw ParsingError()
    }
    return fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<String> {
        var shouldThrow = false
        try {
            word(badChar)(str, indexFrom, scopeStack)
            shouldThrow = true
        } catch (_: NoMatchError) {}
        if (shouldThrow) {
            throw NoMatchError()
        }
        return anyCharParser(str, indexFrom, scopeStack)
    }
}

fun <T1, T2> diff(p1: UniqueParser<T1>, p2: UniqueParser<T2>, strict: Boolean = false): UniqueParser<T1> {
    return fun(str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T1> {
        var shouldThrow = false
        val res1 = p1(str, indexFrom, scopeStack)
        try {
            val res2 = p2(str, indexFrom, scopeStack)
            if(strict || (res1.indexFrom == res2.indexFrom && res1.indexTo == res2.indexTo)) {
                shouldThrow = true
            }
        } catch (_: NoMatchError) {}
        if(shouldThrow) {
            throw NoMatchError()
        }
        return res1
    }
}

fun <T1, T2> strictDiff(p1: UniqueParser<T1>, p2: UniqueParser<T2>): UniqueParser<T1> {
    return diff(p1, p2, strict = true)
}

fun words(vararg words: String): UniqueParser<String> {
    val wordParsers = words.map { w -> word(w) }
    return disjUnion(*wordParsers.toTypedArray())
}

fun <T> optional(p: UniqueParser<T>): UniqueParser<T?> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T?> {
        try {
            return p(str, indexFrom, scopeStack) as ParsingResult<T?>
        } catch (_: NoMatchError) {}
        return ParsingResult(indexFrom, indexFrom, null)
    }
}

fun <T> optional(p: UniqueParser<T>, default: T): UniqueParser<T> {
    return fun (str: String, indexFrom: Int, scopeStack: ScopeStack): ParsingResult<T> {
        try {
            return p(str, indexFrom, scopeStack)
        } catch (_: NoMatchError) {}
        return ParsingResult(indexFrom, indexFrom, default)
    }
}

fun <T> unique(list: List<T>): List<T> {
    val results = ArrayList<T>()

    for(member in list) {
        if(!results.contains(member)) {
            results.add(member)
        }
    }

    return results
}

fun <T> unitify(p: UniqueParser<T>): UniqueParser<Unit> {
    return pack(p) { _ -> }
}

const val RUNTIME_TYPES_CHECK_ON = true
inline fun <reified T1, reified T2> safeCastResult(res: ParsingResult<T1>): ParsingResult<T2> {
    return when(res.found) {
        is T2 -> ParsingResult(res.indexFrom, res.indexTo, res.found)
        else -> {
            val t1Name = if(res.found == null) "null" else (T1::class.simpleName as String)
            throw TypeCastException("Couldn't cast ${res.found} of $t1Name to ${typeOf<T2>()}")
        }
    }
}

inline fun <reified T1, reified T2> safeCastParser(crossinline p: UniqueParser<T1>): UniqueParser<T2> {
    return {str, indexFrom, scopeStack -> safeCastResult(p(str, indexFrom, scopeStack))}
}


inline fun <reified T1, reified T2> safeCastListParser(crossinline p: ListParser<T1>): ListParser<T2> {
    return {str, indexFrom, scopeStack -> p(str, indexFrom, scopeStack).map { res -> safeCastResult(res) }}
}

inline fun <reified T1, reified T2> castResult(res: ParsingResult<T1>): ParsingResult<T2> {
    @Suppress("UNCHECKED_CAST")
    return if(RUNTIME_TYPES_CHECK_ON) safeCastResult(res) else res as ParsingResult<T2>
}

inline fun <reified T1, reified T2> castParser(noinline p: UniqueParser<T1>): UniqueParser<T2> {
    @Suppress("UNCHECKED_CAST")
    return if(RUNTIME_TYPES_CHECK_ON) safeCastParser(p) else p as UniqueParser<T2>
}

inline fun <reified T1, reified T2> castListParser(noinline p: ListParser<T1>): ListParser<T2> {
    @Suppress("UNCHECKED_CAST")
    return if(RUNTIME_TYPES_CHECK_ON) safeCastListParser(p) else p as ListParser<T2>
}




