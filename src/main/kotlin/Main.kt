import com.google.gson.*
import com.parser.*

fun handleCommand(command: Command) {
    when(command) {
        is ParseHeadersCommand -> {
            val results = ArrayList<ExpertProcess>()

            command.content.forEach { parsingRequest ->
                try {
                    val header = processHeaderParser(parsingRequest.content, 0, ScopeStack())
                    val documentation = documentationParser(parsingRequest.content, 0, ScopeStack())

                    val inputs = if (header.found.inputs == null || header.found.inputs.found.vars == null) ArrayList()
                    else header.found.inputs.found.vars.found.map { variable -> litify(variable.found) }
                    val outputs =
                        if (header.found.outputs == null || header.found.outputs.found.vars == null) ArrayList()
                        else header.found.outputs.found.vars.found.map { variable -> litify(variable.found) }
                    val inputsOutputs =
                        if (header.found.inputs_outputs == null || header.found.inputs_outputs.found.vars == null) ArrayList()
                        else header.found.inputs_outputs.found.vars.found.map { variable -> litify(variable.found) }
                    results.add(
                        ExpertProcess(
                            header.found.hebrewName.found.name.found, parsingRequest.englishName, documentation.found,
                            inputs, outputs, inputsOutputs
                        )
                    )
                } catch (_: ParsingError) { }
            }

            val jsonResponse = gson.toJson(results)
            println(jsonResponse.toString())
        }
        is ParseProcessesCommand -> {
            val results = command.content.map { parsingRequest -> ParsedProcessResult(parsingRequest.englishName,
                parseProcess(parsingRequest.content), allCommentsParser(parsingRequest.content, 0, ScopeStack())) }
            val jsonResponse = gson.toJson(results)
            println(jsonResponse).toString()
        }
        is UpdateReservedWordsCommand -> {
            ReservedWordsData.setRvDict(command.reservedWords)
            println("OK")
        }
        else -> {
            error("Unrecognized command: $command")
        }
    }
}
fun main(args: Array<String>) {
    val gsonBuilder = GsonBuilder()
    gsonBuilder.registerTypeAdapter(TaggedCommand::class.java, CommandJsonDeserializer())
    val gson = gsonBuilder.create()


    while (true) {
        val commandStr = readln()
        val command = gson.fromJson(commandStr, TaggedCommand::class.java).command
        handleCommand(command)
    }
}