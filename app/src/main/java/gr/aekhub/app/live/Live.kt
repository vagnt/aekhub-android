package gr.aekhub.app.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * «Σήμα ανανέωσης»: όταν αλλάξει κάτι στο site (σκορ, άρθρο, γεγονός), ο server στέλνει
 * ένα αθόρυβο μήνυμα και όλες οι ανοιχτές οθόνες ανανεώνονται αμέσως.
 * Κάθε σήμα δίνει ΔΥΟ ανανεώσεις: τώρα και ξανά σε 4″, για όσες αλλαγές
 * έγιναν η μία πάνω στην άλλη (π.χ. γκολ + σκόρερ + λεπτό).
 */
object Live {
    private val _ping = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val ping: SharedFlow<Unit> = _ping
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var again: Job? = null
    fun ping() {
        _ping.tryEmit(Unit)
        again?.cancel()
        again = scope.launch { delay(4_000); _ping.tryEmit(Unit) }
    }
}
