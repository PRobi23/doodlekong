package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_NEW_WORDS

data class NewWords(
    val newWords: List<String>
) : BaseModel(TYPE_NEW_WORDS)
