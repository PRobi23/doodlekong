package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_ANNOUNCEMENT

data class Announcement(
    val message: String,
    val timestamp: Long,
    val announcementType: Int,
) : BaseModel(TYPE_ANNOUNCEMENT) {
    companion object {
        const val TYPE_PLAYER_GUESSED_THE_WORD = 0
        const val TYPE_PLAYER_JOINED = 1
        const val TYPE_PLAYER_LEFT = 2
        const val TYPE_EVERYBODY_GUESSED_IT = 3
    }
}
