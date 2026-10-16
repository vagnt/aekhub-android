package gr.aekhub.app.live

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * «Σήμα ανανέωσης»: όταν αλλάξει κάτι στο site (σκορ, άρθρο, γεγονός), ο server στέλνει
 * ένα αθόρυβο μήνυμα και όλες οι ανοιχτές οθόνες ανανεώνονται αμέσως.
 */
object Live {
    private val _ping = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val ping: SharedFlow<Unit> = _ping
    fun ping() { _ping.tryEmit(Unit) }
}
