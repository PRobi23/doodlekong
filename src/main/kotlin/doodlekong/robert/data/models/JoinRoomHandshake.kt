package doodlekong.robert.data.models

import doodlekong.robert.utility.Constants.TYPE_JOIN_ROOM_HANDSHAKE

data class JoinRoomHandshake(
    val userName: String,
    val roomName: String,
    val clientId: String,
) : BaseModel(TYPE_JOIN_ROOM_HANDSHAKE)
