package com.github.echo2124

import com.github.echo2124.Main.constants.activityLog

class Close : Thread() {
    @Override
    fun run() {
        activityLog.sendActivityMsg("[MAIN] Aria bot is restarting...", 1)
        System.out.println("Performing Shutdown Sequence")
    }
}