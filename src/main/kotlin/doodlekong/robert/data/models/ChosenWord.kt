package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_CHOSEN_WORD

data class ChosenWord(
    val chosenWord: String,
    val roomName: String
) : BaseModel(TYPE_CHOSEN_WORD )
