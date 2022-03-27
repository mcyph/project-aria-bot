package com.github.echo2124;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OnCampus {
    public OnCampus() {
        initScheduler();
    }

    public void initScheduler() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Australia/Melbourne"));
        ZonedDateTime nextRun = now.withHour(3).withMinute(32).withSecond(0);
        if(now.compareTo(nextRun) > 0)
            nextRun = nextRun.plusDays(1);

        Duration duration = Duration.between(now, nextRun);
        long initialDelay = duration.getSeconds();
        Runnable task = new Runnable() {
            @Override
            public void run() {
                String checkUnicode="U+2705";
                System.out.println("Running task");
               // remove previous msgs & remove role from everyone
                Role oncampus=Main.constants.jda.getRolesByName(Main.constants.ONCAMPUS_ROLE_NAME, true).get(0);

                // re-ref role
                TextChannel msgChannel= Main.constants.jda.getTextChannelsByName(Main.constants.ONCAMPUS_CHANNEL_NAME, true).get(0);
                // recreating channel
                msgChannel.delete().queue();
                msgChannel.createCopy().queue(textChannel -> {
                    // consider adding date to this msg
                    System.out.println("new msgChannelID: "+textChannel.getId());
                    textChannel.sendMessage("React below to the following emoji listed if you are heading to campus today").queue(message -> {
                        message.addReaction(checkUnicode);
                        // recreating role
                        oncampus.delete().queue();
                        oncampus.createCopy().queue(role -> {
                            ListenerAdapter s = new ListenerAdapter() {
                                @Override
                                public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
                                    if (event.getMessageId().equals(message.getId()) && event.getReactionEmote().getName().equals("white_check_mark")) {
                                        event.getGuild().addRoleToMember(event.getMember(),role);
                                    }
                                    super.onMessageReactionAdd(event);
                                }
                            };
                        });
                        });
                });

                // generate msg

            }
        };
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(task,
                initialDelay,
                TimeUnit.DAYS.toSeconds(1),
                TimeUnit.SECONDS);
    }



}
