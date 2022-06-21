package com.github.echo2124

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.entities.MessageChannel

class ActivityLog {
    // 1 = info; 2=warn; 3=error;
    fun sendActivityMsg(msg: String, type: Int) {
        val now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Australia/Melbourne"))
        val dtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy - HH:mm:ss Z")
        val msgToSend = when (type) {
            1 -> "```[INFO] [${now.format(dtFormatter)}] yaml\n ${msg}\n```"
            2 -> "```[WARN] [${now.format(dtFormatter)}] fix\n ${msg}\n```"
            3 -> "```[ERROR] [${now.format(dtFormatter)}] diff\n- ${msg} - \n```"
        }

        val msgChannel: TextChannel = Main.constants.jda.getTextChannelById(Main.constants.ACTIVITY_LOG_ID)
        if (type == 3) {
            val user = Main.constants.jda.getUserById(Main.constants.DEVELOPER_ID)
            msgChannel.sendMessage(msgToSend + user.getAsMention()).queue()
        } else {
            msgChannel.sendMessage(msgToSend).queue()
        }
    }
}
