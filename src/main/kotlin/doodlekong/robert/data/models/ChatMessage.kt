package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_CHAT_MESSAGE

data class ChatMessage(
    val from: String,
    val roomName: String,
    val message: String,
    val timeStamp: String,
) : BaseModel(TYPE_CHAT_MESSAGE)
