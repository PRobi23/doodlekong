package doodlekong.robert.data

import doodlekong.robert.data.models.*
import doodlekong.robert.gson
import doodlekong.robert.server
import doodlekong.robert.utility.getRandomWords
import doodlekong.robert.utility.matchesWord
import doodlekong.robert.utility.transformToUnderscores
import doodlekong.robert.utility.words
import io.ktor.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

data class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = emptyList(), // TODO Use copy
) {

    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<String>()
    private var word: String? = null
    private var currentWords: List<String>? = null
    private var drawingPlayerIndex: Int = 0
    private var startTime = 0L

    private var playerRemoveJobs = ConcurrentHashMap<String, Job>()
    private var leftPlayers = ConcurrentHashMap<String, Pair<Player, Int>>()

    private var phaseChangedListener: ((Phase) -> Unit)? = null

    private var currentRoundDrawData: List<String> = emptyList()
    var lastDrawData: DrawData? = null

    var phase = Phase.WAITING_FOR_PLAYERS
        set(value) {
            synchronized(field) {
                field = value
                phaseChangedListener?.let { change ->
                    change(value)
                }
            }
        }

    private fun setPhaseChangedListener(listener: (Phase) -> Unit) {
        phaseChangedListener = listener
    }

    init {
        setPhaseChangedListener { newPhase ->
            when (newPhase) {
                Phase.WAITING_FOR_PLAYERS -> waitingForPlayers()
                Phase.WAITING_FOR_START -> waitingForStart()
                Phase.NEW_ROUND -> newRound()
                Phase.GAME_RUNNING -> gameRunning()
                Phase.SHOW_WORD -> showWord()
            }
        }
    }

    suspend fun addPlayer(clientId: String, userName: String, socket: WebSocketSession): Player {
        var indexToAdd = players.size - 1
        val player = if (leftPlayers.containsKey(clientId)) {
            val leftPlayer = leftPlayers[clientId]
            leftPlayer?.first?.let {
                it.socket = socket
                it.isDrawing = drawingPlayer?.clientId == clientId
                indexToAdd = leftPlayer.second

                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                leftPlayers.remove(clientId)
                it
            } ?: Player(userName, socket, clientId)
        } else {
            Player(userName, socket, clientId)

        }
        indexToAdd = when {
            players.isEmpty() -> 0
            indexToAdd >= players.size -> players.size - 1
            else -> indexToAdd
        }
        val tempPlayers = players.toMutableList()
        tempPlayers.add(indexToAdd, player)
        players = tempPlayers.toList()

        if (players.size == 1) {
            phase = Phase.WAITING_FOR_PLAYERS
        } else if (players.size == 2 && phase == Phase.WAITING_FOR_PLAYERS) {
            phase = Phase.WAITING_FOR_START
            players = players.shuffled()
        } else if (phase == Phase.WAITING_FOR_START && players.size == maxPlayers) {
            phase = Phase.NEW_ROUND
            players = players.shuffled()
        }

        val announcement = Announcement(
            "$userName joined the party!",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )

        sendWordToPlayer(player)
        broadcastPlayerStates()
        sendCurRoundDrawInfoToPlayer(player)
        broadcast(gson.toJson(announcement))

        return player
    }

    private suspend fun sendCurRoundDrawInfoToPlayer(player: Player) {
        if (phase == Phase.GAME_RUNNING || phase == Phase.SHOW_WORD) {
            player.socket.send(Frame.Text(gson.toJson(RoundDrawInfo(currentRoundDrawData))))
        }
    }

    private suspend fun finishOffDrawing() {
        lastDrawData?.let {
            //2 -> ACTION_MOVE
            if (currentRoundDrawData.isNotEmpty() && it.motionEvent == 2) {
                val finishDrawData = it.copy(
                    motionEvent = 1 //ACTION_UP
                )
                broadcast(gson.toJson(finishDrawData))
            }
        }
    }

    fun addSerializedDrawInfo(drawAction: String) {
        currentRoundDrawData = currentRoundDrawData + drawAction
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun removePlayer(clientId: String) {
        val player = players.find { it.clientId == clientId } ?: return
        val index = players.indexOf(player)
        leftPlayers[clientId] = player to index
        players = players - player

        playerRemoveJobs[clientId] = GlobalScope.launch {
            delay(PLAYER_REMOVE_TIME)
            val playerToRemove = leftPlayers[clientId]
            leftPlayers.remove(clientId)
            playerToRemove?.let {
                players = players - it.first
            }
            playerRemoveJobs.remove(clientId)
        }

        val announcement = Announcement(
            "${player.userName} left the party :(",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_LEFT
        )

        GlobalScope.launch {
            broadcastPlayerStates()
            broadcast(gson.toJson(announcement))
            if (players.size == 1) {
                phase = Phase.WAITING_FOR_PLAYERS
                timerJob?.cancel()
            } else if (players.isEmpty()) {
                kill()
                server.rooms.remove(name)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun timeAndNotify(ms: Long) {
        timerJob?.cancel()
        timerJob = GlobalScope.launch {
            startTime = System.currentTimeMillis()
            val phaseChange = PhaseChange(
                phase = phase,
                time = ms,
                drawingPlayer = drawingPlayer?.userName
            )
            repeat((ms / UPDATE_TIME_FREQUENCY).toInt()) {
                if (it != 0) {
                    phaseChange.phase = null
                }
                broadcast(gson.toJson(phaseChange))
                phaseChange.time -= UPDATE_TIME_FREQUENCY
                delay(UPDATE_TIME_FREQUENCY)
            }
            phase = when (phase) {
                Phase.WAITING_FOR_START -> Phase.NEW_ROUND
                Phase.GAME_RUNNING -> {
                    finishOffDrawing()
                    Phase.SHOW_WORD
                }

                Phase.SHOW_WORD -> Phase.NEW_ROUND
                Phase.NEW_ROUND -> {
                    word = null
                    Phase.GAME_RUNNING
                }

                else -> Phase.WAITING_FOR_PLAYERS
            }
        }
    }

    suspend fun broadcast(message: String) {
        players.forEach { player ->
            if (player.socket.isActive) {
                player.socket.send(
                    Frame.Text(message)
                )
            }
        }
    }

    suspend fun broadcastAllExcept(message: String, clientId: String) {
        players.forEach { player ->
            if (player.clientId != clientId && player.socket.isActive) {
                player.socket.send(
                    Frame.Text(message)
                )
            }
        }
    }

    fun containsPlayer(userName: String?): Boolean {
        return players.find { it.userName == userName } != null
    }

    fun setWordAndSwitchToGameRunning(word: String) {
        this.word = word
        phase = Phase.GAME_RUNNING
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun waitingForPlayers() {
        GlobalScope.launch {
            val phaseChange = PhaseChange(
                Phase.WAITING_FOR_PLAYERS,
                DELAY_WAITING_FOR_START_NEW_ROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun waitingForStart() {
        GlobalScope.launch {
            timeAndNotify(DELAY_WAITING_FOR_START_NEW_ROUND)
            val phaseChange = PhaseChange(
                Phase.WAITING_FOR_START,
                DELAY_WAITING_FOR_START_NEW_ROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun newRound() {
        currentRoundDrawData = emptyList()
        currentWords = getRandomWords(3)
        val newWords = NewWords(currentWords!!)
        nextDrawingPlayer()
        GlobalScope.launch {
            broadcastPlayerStates()
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(newWords)))
            timeAndNotify(DELAY_NEW_ROUND_TO_GAME_RUNNING)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun gameRunning() {
        winningPlayers = listOf()
        val wordToSend = word ?: currentWords?.random() ?: words.random()
        val wordWithUnderscores = wordToSend.transformToUnderscores()
        val drawingUserName = (drawingPlayer ?: players.random()).userName

        val gameStateForDrawingPlayer = GameState(
            drawingUserName,
            wordToSend
        )

        val gameStateForGuessingPlayer = GameState(
            drawingUserName,
            wordWithUnderscores
        )
        GlobalScope.launch {
            broadcastAllExcept(
                gson.toJson(gameStateForGuessingPlayer),
                drawingPlayer?.clientId ?: players.random().clientId
            )
            drawingPlayer?.socket?.send(Frame.Text(gson.toJson(gameStateForDrawingPlayer)))

            timeAndNotify(DELAY_GAME_RUNNING_TO_SHOW_WORD)

            println("Drawing phase in room $name started. It'll  last ${DELAY_GAME_RUNNING_TO_SHOW_WORD / 1000}seconds")
        }
    }

    private fun isGuessCorrect(guess: ChatMessage): Boolean {
        return guess.matchesWord(word ?: return false) && !winningPlayers.contains(guess.from) &&
                guess.from != drawingPlayer?.userName && phase == Phase.GAME_RUNNING
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showWord() {
        GlobalScope.launch {
            if (winningPlayers.isEmpty()) {
                drawingPlayer?.let {
                    it.score -= PENALTY_NOBODY_GUESSED_IT
                }
            }
            broadcastPlayerStates()
            word?.let {
                val chosenWord = ChosenWord(
                    it,
                    name
                )
                broadcast(gson.toJson(chosenWord))
            }
            timeAndNotify(DELAY_SHOW_WORD_TO_NEW_ROUND)
            val phaseChange = PhaseChange(
                Phase.SHOW_WORD,
                DELAY_SHOW_WORD_TO_NEW_ROUND
            )
            broadcast(gson.toJson(phaseChange))
        }
    }

    private fun addWinningPlayer(userName: String): Boolean {
        winningPlayers = winningPlayers + userName
        if (winningPlayers.size == players.size - 1) {
            phase = Phase.NEW_ROUND
            return true
        }
        return false
    }

    suspend fun checkWordAndNotifyPlayers(message: ChatMessage): Boolean {
        if (isGuessCorrect(message)) {
            val guessingTime = System.currentTimeMillis() - startTime
            val timePercentageLeft = 1f - guessingTime.toFloat() / DELAY_GAME_RUNNING_TO_SHOW_WORD
            val score = GUESS_SCORE_DEFAULT + GUESS_SCORE_PERCENTAGE_MULTIPLIER * timePercentageLeft
            val player = players.find { it.userName == message.from }

            player?.let {
                it.score += score.toInt()
            }
            drawingPlayer?.let {
                it.score += GUESS_SCORE_FOR_DRAWING_PLAYER / players.size
            }

            broadcastPlayerStates()

            val announcement = Announcement(
                "${message.from} has guessed it",
                System.currentTimeMillis(),
                Announcement.TYPE_PLAYER_GUESSED_THE_WORD
            )
            broadcast(gson.toJson(announcement))

            val isRoundOver = addWinningPlayer(message.from)
            if (isRoundOver) {
                val roundOverAnnouncement = Announcement(
                    "Everybody guessed it. New round is starting",
                    System.currentTimeMillis(),
                    Announcement.TYPE_EVERYBODY_GUESSED_IT
                )
                broadcast(gson.toJson(roundOverAnnouncement))
            }
            return true
        }
        return false
    }

    private suspend fun broadcastPlayerStates() {
        val playersList = players.sortedByDescending { it.score }.map {
            PlayerData(
                it.userName, it.isDrawing, it.score, it.rank
            )
        }
        playersList.forEachIndexed { index, playerData ->
            playerData.rank = index + 1
        }
        broadcast(gson.toJson(PlayersList(playersList)))
    }

    private suspend fun sendWordToPlayer(player: Player) {
        val delay = when (phase) {
            Phase.WAITING_FOR_START -> DELAY_WAITING_FOR_START_NEW_ROUND
            Phase.NEW_ROUND -> DELAY_NEW_ROUND_TO_GAME_RUNNING
            Phase.GAME_RUNNING -> DELAY_GAME_RUNNING_TO_SHOW_WORD
            Phase.SHOW_WORD -> DELAY_SHOW_WORD_TO_NEW_ROUND
            Phase.WAITING_FOR_PLAYERS -> TODO()
        }
        val phaseChange = PhaseChange(
            phase,
            delay,
            drawingPlayer?.userName
        )

        word?.let { currentWord ->
            drawingPlayer?.let { drawingPlayer ->
                val gameState = GameState(
                    drawingPlayer.userName,
                    if (player.isDrawing || phase == Phase.SHOW_WORD) {
                        currentWord
                    } else {
                        currentWord.transformToUnderscores()
                    }
                )
                player.socket.send(Frame.Text(gson.toJson(gameState)))
            }
        }
        player.socket.send(Frame.Text(gson.toJson(phaseChange)))
    }

    private fun nextDrawingPlayer() {
        drawingPlayer?.isDrawing = false
        if (players.isEmpty()) {
            return
        }

        drawingPlayer = if (drawingPlayerIndex < players.size - 1) {
            players[drawingPlayerIndex]
        } else {
            players.last()
        }

        if (drawingPlayerIndex < players.size - 1) {
            drawingPlayerIndex++
        } else {
            drawingPlayerIndex = 0
        }
    }

    enum class Phase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        GAME_RUNNING,
        SHOW_WORD
    }

    private fun kill() {
        playerRemoveJobs.values.forEach { job -> job.cancel() }
        timerJob?.cancel()
    }

    companion object {
        const val UPDATE_TIME_FREQUENCY = 1000L
        const val PLAYER_REMOVE_TIME = 60000L

        const val DELAY_WAITING_FOR_START_NEW_ROUND = 10000L
        const val DELAY_NEW_ROUND_TO_GAME_RUNNING = 20000L
        const val DELAY_GAME_RUNNING_TO_SHOW_WORD = 60000L
        const val DELAY_SHOW_WORD_TO_NEW_ROUND = 10000L

        const val PENALTY_NOBODY_GUESSED_IT = 50
        const val GUESS_SCORE_DEFAULT = 50
        const val GUESS_SCORE_PERCENTAGE_MULTIPLIER = 50
        const val GUESS_SCORE_FOR_DRAWING_PLAYER = 50
    }
}