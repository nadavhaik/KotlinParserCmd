package com.parser
data class ParsingRequest(val englishName: String, val content: String)
data class ParsedProcessResult(val englishName: String, val parsedProcess: ParsingResult<ParsedProcess>,
                               val parsedComments: List<ParsingResult<ExpertComment>>)

