package com.github.echo2124

import java.time.ZoneId
import java.time.Duration
import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjuster
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.restaction.RoleAction
import net.dv8tion.jda.api.exceptions.ErrorResponseException
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent

import com.github.echo2124.Main.constants.*

class OnCampus(state: Boolean) : ListenerAdapter() {
    val checkUnicode = "U+2705"

    init {
        initScheduler(state)
        restoreListener()
    }

    fun initScheduler(state: Boolean) {
        val now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Australia/Melbourne"))
        var generateNextRun: ZonedDateTime = now.withHour(6).withMinute(30).withSecond(0)
        var resetNextRun: ZonedDateTime = now.withHour(0).withMinute(0).withSecond(0)
        if (now.compareTo(generateNextRun) > 0) generateNextRun = generateNextRun.plusDays(1)
        val generateDuration: Duration = Duration.between(now, generateNextRun)
        if (now.compareTo(resetNextRun) > 0) resetNextRun = resetNextRun.plusDays(1)
        val resetDuration: Duration = Duration.between(now, resetNextRun)
        val generateInitialDelay: Long = generateDuration.getSeconds()
        val resetInitialDelay: Long = resetDuration.getSeconds()
        val generateHandler: Runnable = object : Runnable() {
            @Override
            fun run() {
                activityLog.sendActivityMsg("[ONCAMPUS] Running generate task", 1)
                val calendar: Calendar = Calendar.getInstance()
                val day: Int = calendar.get(Calendar.DAY_OF_WEEK)
                val guild: Guild = Main.constants.jda.getGuilds().get(0)
                // test
                System.out.println("[OnCampus] Running task")
                val oncampus: Role = Main.constants.jda.getRoleById(ONCAMPUS_ROLE_ID)
                val msgChannel: TextChannel = Main.constants.jda.getTextChannelById(ONCAMPUS_CHANNEL_ID)
                resetEntities(oncampus, msgChannel, guild)
                if (day != Calendar.SUNDAY && day != Calendar.SATURDAY || state) {
                    generateMsg(oncampus, msgChannel)
                }
            }
        }
        val resetHandler: Runnable = object : Runnable() {
            @Override
            fun run() {
                activityLog.sendActivityMsg("[ONCAMPUS] Running reset task", 1)
                val oncampus: Role = Main.constants.jda.getRoleById(ONCAMPUS_ROLE_ID)
                val msgChannel: TextChannel = Main.constants.jda.getTextChannelById(ONCAMPUS_CHANNEL_ID)
                val guild: Guild = Main.constants.jda.getGuilds().get(0)
                resetEntities(oncampus, msgChannel, guild)
            }
        }
        val generateScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
        generateScheduler.scheduleAtFixedRate(generateHandler,
                generateInitialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS)
        val resetScheduler: ScheduledExecutorService = Executors.newScheduledThreadPool(1)
        resetScheduler.scheduleAtFixedRate(resetHandler,
                resetInitialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS)
        if (state) {
            generateHandler.run()
        }
    }

    fun resetEntities(oncampus: Role?, msgChannel: TextChannel, guild: Guild) {
        guild.loadMembers()
        val msgHistory: MessageHistory = msgChannel.getHistory()
        try {
            msgHistory.retrievePast(1).queue { messages -> messages.get(0).delete().queue() }
        } catch (e: Exception) {
            activityLog.sendActivityMsg("[ONCAMPUS] Unable to fetch last message", 2)
            System.out.println("[OnCampus] Unable to grab last message")
        }
        try {
            val members: Collection<Member> = guild.getMembersWithRoles(oncampus)
            for (member in members) {
                guild.removeRoleFromMember(member, oncampus).queue()
            }
        } catch (e: Exception) {
            System.out.println("[OnCampus] Unable to remove role from users")
            activityLog.sendActivityMsg("[ONCAMPUS] No users to remove role from", 2)
        }
        activityLog.sendActivityMsg("[ONCAMPUS] Removed old On Campus message & removed all users from role", 1)
    }

    fun generateMsg(oncampus: Role?, msgChannel: TextChannel) {
        val embed = EmbedBuilder()
        embed.setTitle("Who Is On Campus today?")
        embed.setDescription("React to the existing reaction below to assign yourself to the OnCampus role")
        embed.setAuthor("IT @ Monash")
        embed.setColor(Color.CYAN)
        embed.setFooter("NOTE: This post will be recreated everyday & role will be removed from everyone")
        msgChannel.sendMessageEmbeds(embed.build()).queue { message ->
            message.addReaction(checkUnicode).queue()
            val reactionListener: ListenerAdapter = object : ListenerAdapter() {
                @Override
                fun onMessageReactionAdd(@NotNull event: MessageReactionAddEvent) {
                    if (event.getMessageId().equals(message.getId()) &&
                            event.getReactionEmote().getName().equals("✅") &&
                            !event.getMember().getUser().isBot()
                    ) {
                        activityLog.sendActivityMsg("[ONCAMPUS] React Listener triggered", 1)
                        System.out.println("[OnCampus] Added role to member")
                        activityLog.sendActivityMsg("[ONCAMPUS] Giving On Campus role to user", 1)
                        event.getGuild().addRoleToMember(event.getMember(), oncampus).queue()
                    }
                    super.onMessageReactionAdd(event)
                }
            }
            Main.constants.jda.addEventListener(reactionListener)
        }
        activityLog.sendActivityMsg("[ONCAMPUS] Generated OnCampus Message", 1)
    }

    fun restoreListener() {
        val oncampus: Role = Main.constants.jda.getRoleById(ONCAMPUS_ROLE_ID)
        val msgChannel: TextChannel = Main.constants.jda.getTextChannelById(ONCAMPUS_CHANNEL_ID)
        val msgHistory: MessageHistory = msgChannel.getHistory()
        val now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Australia/Melbourne"))
        activityLog.sendActivityMsg("[ONCAMPUS] Attempting to restore listener...", 1)
        try {
            msgHistory.retrievePast(1).queue { messages ->
                // checks if last oncampus message was made same day if so then try to reattach the listener
                try {
                    if (messages.get(0).getTimeCreated()
                                       .atZoneSameInstant(ZoneId.of("Australia/Melbourne"))
                                       .getDayOfWeek()
                                       .compareTo(now.getDayOfWeek()) === 0) {
                        try {
                            val reactionListener: ListenerAdapter = object : ListenerAdapter() {
                                @Override
                                fun onMessageReactionAdd(@NotNull event: MessageReactionAddEvent) {
                                    if (event.getMessageId().equals(messages.get(0).getId()) &&
                                            event.getReactionEmote().getName().equals("✅") &&
                                            !event.getMember().getUser().isBot()
                                    ) {
                                        activityLog.sendActivityMsg("[ONCAMPUS] React Listener triggered", 1)
                                        System.out.println("[OnCampus] Added role to member")
                                        activityLog.sendActivityMsg("[ONCAMPUS] Giving On Campus role to user", 1)
                                        event.getGuild().addRoleToMember(event.getMember(), oncampus).queue()
                                    }
                                }
                            }
                            Main.constants.jda.addEventListener(reactionListener)
                            activityLog.sendActivityMsg("[ONCAMPUS] Restore successful, attached listener!", 1)
                        } catch (e: Exception) {
                            activityLog.sendActivityMsg("[ONCAMPUS] Unable to restore: cannot attach listener", 2)
                        }
                    }
                } catch (e: Exception) {
                    activityLog.sendActivityMsg("[ONCAMPUS] Unable to restore: cannot fetch last message", 1)
                }
            }
        } catch (e: Exception) {
            activityLog.sendActivityMsg("[ONCAMPUS] Unable to restore: cannot fetch last message", 1)
            System.out.println("[OnCampus] Unable to grab last message")
        }
    }
}