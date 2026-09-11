package com.wanderwildwood.kirinuki

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ApplicationCoroutineScope : CoroutineScope {
    override val coroutineContext = Dispatchers.Default + SupervisorJob()
}
