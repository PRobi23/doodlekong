package doodlekong.robert.data.models

import doodlekong.robert.data.Room
import doodlekong.robert.utility.Constants.TYPE_PHASE_CHANGE

data class PhaseChange(
    var phase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null
) : BaseModel(TYPE_PHASE_CHANGE)
