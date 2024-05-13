package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_GAME_STATE

data class GameState(
    val drawingPlayer: String,
    val world: String
) : BaseModel(TYPE_GAME_STATE)