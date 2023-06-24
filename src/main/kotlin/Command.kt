import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import com.parser.ParsingRequest

val gson = Gson()


interface Command
data class ParseHeadersCommand(val content: List<ParsingRequest>) : Command
data class ParseProcessesCommand(val content: List<ParsingRequest>) : Command
data class UpdateReservedWordsCommand(val reservedWords: Map<String, List<String>>) : Command

data class TaggedCommand(val tag: String, val command: Command)

class CommandJsonDeserializer: JsonDeserializer<TaggedCommand> {
    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): TaggedCommand {
//        val jsonObject = json!!.asJsonObject
        val root = json!!.asJsonObject
        val tag = root!!.get("tag")!!.asString
        val commandContent = root.get("command").asJsonObject!!
        var obj: Command? = null
        when(tag ?: "") {
            "ParseHeadersCommand" -> {
                val typeToken = object : TypeToken<List<ParsingRequest>>() {}.type
                val content = gson.fromJson<List<ParsingRequest>>(commandContent.get("content"), typeToken)
                obj = ParseHeadersCommand(content)
            }
            "ParseProcessesCommand" -> {
                val typeToken = object : TypeToken<List<ParsingRequest>>() {}.type
                val content = gson.fromJson<List<ParsingRequest>>(commandContent.get("content"), typeToken)
                obj = ParseProcessesCommand(content)
            }
            "UpdateReservedWordsCommand" -> {
                val empMapType = object : TypeToken<Map<String, List<String>>>() {}.type
                val reservedWords = gson.fromJson<Map<String, List<String>>>(commandContent.get("reservedWords"), empMapType)
                obj = UpdateReservedWordsCommand(reservedWords)
            }
        }
        return TaggedCommand(tag, obj!!)
    }
}


