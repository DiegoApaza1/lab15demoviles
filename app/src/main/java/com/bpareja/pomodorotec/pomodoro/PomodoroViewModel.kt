package com.bpareja.pomodorotec.pomodoro

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.RingtoneManager
import android.os.CountDownTimer
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.bpareja.pomodorotec.MainActivity
import com.bpareja.pomodorotec.PomodoroReceiver
import com.bpareja.pomodorotec.R

enum class Phase {
    FOCUS, BREAK
}

class PomodoroViewModel(application: Application) : AndroidViewModel(application) {
    init {
        instance = this
    }

    // Singleton para acceder al ViewModel desde el BroadcastReceiver
    companion object {
        private var instance: PomodoroViewModel? = null
        fun skipBreak() {
            instance?.startFocusSession()  // Saltar el descanso y comenzar sesión de concentración
        }
    }

    private val context = getApplication<Application>().applicationContext

    // Estados observables (LiveData)
    private val _timeLeft = MutableLiveData("25:00") // Tiempo mostrado en UI
    val timeLeft: LiveData<String> = _timeLeft

    private val _isRunning = MutableLiveData(false) // Estado del timer
    val isRunning: LiveData<Boolean> = _isRunning

    private val _currentPhase = MutableLiveData(Phase.FOCUS)// Fase actual
    val currentPhase: LiveData<Phase> = _currentPhase

    private val _isSkipBreakButtonVisible = MutableLiveData(false)// Visibilidad botón saltar
    val isSkipBreakButtonVisible: LiveData<Boolean> = _isSkipBreakButtonVisible

    private val _progress = MutableLiveData(0f) // Progreso (0-1)
    val progress: LiveData<Float> = _progress

    // Variables de control del timer
    private var countDownTimer: CountDownTimer? = null

    private var totalTimeInMillis: Long = 25 * 60 * 1000L // Tiempo total (25 min)

    private var timeRemainingInMillis: Long = 25 * 60 * 1000L // Tiempo inicial para FOCUS

    // Función para iniciar la sesión de concentración
    fun startFocusSession() {
        countDownTimer?.cancel() // Cancela cualquier temporizador en ejecución
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 1 * 15 * 1000L // Restablece el tiempo de enfoque a 25 minutos
        totalTimeInMillis = timeRemainingInMillis
        _timeLeft.value = "25:00"
        _progress.value = 0f
        _isSkipBreakButtonVisible.value = false // Ocultar el botón si estaba visible
        showNotification("Inicio de Concentración", "La sesión de concentración ha comenzado.")
        startTimer() // Inicia el temporizador con el tiempo de enfoque actualizado
    }

    // Función para iniciar la sesión de descanso
    private fun startBreakSession() {
        _currentPhase.value = Phase.BREAK
        timeRemainingInMillis = 5 * 60 * 1000L // 5 minutos para descanso
        totalTimeInMillis = timeRemainingInMillis
        _timeLeft.value = "05:00"
        _progress.value = 0f
        _isSkipBreakButtonVisible.value = true // Mostrar el botón durante el descanso
        showNotification("Inicio de Descanso", "La sesión de descanso ha comenzado.")
        startTimer()
    }

    // Inicia o reanuda el temporizador
    fun startTimer() {
        countDownTimer?.cancel()// Cancela cualquier temporizador en ejecución antes de iniciar uno nuevo
        _isRunning.value = true

        countDownTimer = object : CountDownTimer(timeRemainingInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeRemainingInMillis = millisUntilFinished
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                _timeLeft.value = String.format("%02d:%02d", minutes, seconds)

                // Calcular y actualizar el progreso
                val progress = 1f - (millisUntilFinished.toFloat() / totalTimeInMillis.toFloat())
                _progress.value = progress
            }

            override fun onFinish() {
                _isRunning.value = false
                _progress.value = 1f
                when (_currentPhase.value) {
                    Phase.FOCUS -> startBreakSession()
                    Phase.BREAK -> startFocusSession()
                    null -> TODO()
                }
            }
        }.start()
    }

    // Pausa el temporizador
    fun pauseTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
    }

    // Restablece el temporizador
    fun resetTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _currentPhase.value = Phase.FOCUS
        timeRemainingInMillis = 25 * 60 * 1000L // Restablece a 25 minutos
        totalTimeInMillis = timeRemainingInMillis
        _timeLeft.value = "25:00"
        _progress.value = 0f
        _isSkipBreakButtonVisible.value = false // Ocultar el botón al restablecer
    }

    private fun showNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        // Mensajes motivacionales según la fase
        val motivationalMessages = when (_currentPhase.value) {
            Phase.FOCUS -> listOf(
                "¡Estás logrando grandes cosas! 💪",
                "Cada minuto cuenta, sigue así! 🚀",
                "Recuerda: Enfócate en una cosa a la vez 🌟"
            )
            Phase.BREAK -> listOf(
                "¡Tiempo de relajarte un poco! ☕",
                "¡Gran trabajo! Ahora recarga energías 🌿",
                "Usa este descanso para despejar tu mente 🧘"
            )
            else -> listOf(message)
        }
        val randomMessage = motivationalMessages.random()

        // Personalización del título
        val customTitle = when (_currentPhase.value) {
            Phase.FOCUS -> "🎯 ¡Es tu momento de brillar!"
            Phase.BREAK -> "☕ ¡Tómate un respiro merecido!"
            else -> title
        }

        // Personalización del color según la fase
        val notificationColor = when (_currentPhase.value) {
            Phase.FOCUS -> Color.rgb(255, 69, 0) // Naranja vivo
            Phase.BREAK -> Color.rgb(60, 179, 113) // Verde vibrante
            else -> Color.rgb(70, 130, 180) // Azul suave
        }

        // Patrón de vibración según la fase
        val vibrationPattern = when (_currentPhase.value) {
            Phase.FOCUS -> longArrayOf(0, 300, 300, 300)
            Phase.BREAK -> longArrayOf(0, 200, 100, 200)
            else -> longArrayOf(0, 250, 250, 250)
        }

        // Crear la notificación con BigTextStyle para más detalles
        val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(customTitle)
            .setContentText(randomMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setColor(notificationColor)
            .setColorized(true) // Colorear toda la notificación
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(vibrationPattern) // Patrón de vibración
            .setLights(notificationColor, 1000, 1000) // Luz LED personalizada
            .setStyle(NotificationCompat.BigTextStyle().bigText(randomMessage)) // Mostrar texto expandido

        // Acción adicional para cambiar de fase
        if (_currentPhase.value == Phase.FOCUS) {
            val breakIntent = Intent(context, PomodoroReceiver::class.java).apply {
                action = "START_BREAK"
            }
            val breakPendingIntent = PendingIntent.getBroadcast(
                context, 0, breakIntent, PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "Tomar Descanso",
                breakPendingIntent
            )
        } else if (_currentPhase.value == Phase.BREAK) {
            val focusIntent = Intent(context, PomodoroReceiver::class.java).apply {
                action = "START_FOCUS"
            }
            val focusPendingIntent = PendingIntent.getBroadcast(
                context, 0, focusIntent, PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_launcher_foreground,
                "Volver a Trabajar",
                focusPendingIntent
            )
        }

        with(NotificationManagerCompat.from(context)) {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notify(MainActivity.NOTIFICATION_ID, builder.build())
            }
        }
    }
}