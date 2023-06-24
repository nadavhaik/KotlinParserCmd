package com.parser

object ReservedWordsData {
    private var rvDict: Map<String, List<String>>? = null

    fun setRvDict(rvDict: Map<String, List<String>>) {
        this.rvDict = HashMap(rvDict)
    }

    fun getReservedWordTranslations(rv: String): List<String>? {
        if(rvDict == null) {
            return ArrayList()
        }
        return rvDict!![rv]
    }

}