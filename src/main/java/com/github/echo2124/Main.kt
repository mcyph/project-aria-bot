package com.github.echo2124

import net.dv8tion.jda.api.EmbedBuilder

class Main : ListenerAdapter() {
    //******************************
    //****CONFIG***
    //******************************
    object constants {
        val logPrefixes = arrayOf("Module", "ERROR")
        const val enableSocialForwarding = false
        const val enableSSOVerification = false
        const val enableTesting = true
        var jda: JDA? = null
        const val IT_SERVER = "802526304745553930"
        val permittedChannelsTest = arrayOf(
                "912353229285765172",  // verify channel
                "912353440749985852" // admin channel
        )

        // for actual location
        var permittedChannels = arrayOf(
                "913081082298114058",  // verify channel
                "913082023483174922" // admin channel
        )
        const val VERIFIED_ROLE_ID_TEST = "909827233194070039"
        var VERIFIED_ROLE_ID = "912001525432320031"
        var VERIFY_TIMEOUT_ROLE_ID = "914896421965148160"
        const val NEWS_CHANNEL_TEST = "912355120723943424"

        // for monash news
        var NEWS_CHANNEL = "913082864080392213"
        const val COVID_UPDATE_CHANNEL_TEST = "912726004886294569"
        var COVID_UPDATE_CHANNEL = "913081128188014592"
        var db: Database? = null
        var serviceMode = false
        var ONCAMPUS_ROLE_ID = "980368698017718272"
        var ONCAMPUS_CHANNEL_ID = "978762060655632424"
        var EXPOSURE_SITE_CHANNEL = "951902910759977001"
        var ARIA_CHANNEL_CATEGORY_ID = "913080878094241892"
        var ACTIVITY_LOG_ID = "981605485138567228"
        var DEVELOPER_ID = "538660576704856075"
        var activityLog: ActivityLog? = null
    }

    fun onMessageReceived(event: MessageReceivedEvent) {
        val msg: Message = event.getMessage()
        val user: User = event.getAuthor()
        val channel: MessageChannel = event.getChannel()
        val msgContents: String = msg.getContentRaw()

        var news: News
        if (msgContents.contains(">")) {
            if (channel.getId().equals(constants.permittedChannels[0])) {
                if (msgContents.equals(">verify")) {
                    val newVerify = SSOVerify(user, event.getGuild(), channel, db)
                    newVerify.start()
                    // add timeout here. After 5 mins check if user is verified if not then return failure msg (timeout)
                } else if (msgContents.equals(">about")) {
                    val embed = EmbedBuilder()
                    embed.setColor(Color.CYAN)
                    embed.setTitle("About me")
                    embed.setDescription("I am Aria, I help out the staff on the server with various administrative tasks and other stuff.")
                    embed.addField("Why am I called Aria?", "My name is actually an acronym: **A**dministrate, **R**elay, **I**dentify, **A**ttest. I was built to cater to this functionality.", false)
                    embed.addField("Who built me?", "I was built entirely by Echo2124 (Joshua) as a side project that aims to automate many different tasks, such as verifying users, automatically relaying local COVID information & announcements from Monash Uni.", false)
                    channel.sendMessageEmbeds(embed.build()).queue()
                    activityLog.sendActivityMsg("[MAIN] about command triggered!", 1)
                } else if (msgContents.equals(">help")) {
                    val embed = EmbedBuilder()
                    embed.setColor(Color.MAGENTA)
                    embed.setTitle("Commands")
                    embed.setDescription("Here are the following commands that you are able to use")
                    embed.addField(">verify",
                            "This command will initiate a verification check for the user. " +
                            "You will be sent a private message with information related to this.",
                            false
                    )
                    embed.addField(">verifyinfo",
                            "This command will return any collected information associated with your discord id when you were verified. " +
                            "You will be sent a private message with information related to this.",
                            false
                    )
                    embed.addField(">about", "Details information about the bot", false)
                    embed.addField("[ADMIN ONLY] >userLookup <discordID>",
                            "This command will lookup a user's verification status and other recorded details.",
                            false
                    )
                    embed.addField("[WIP - ADMIN ONLY] >userUpdate <discordID>",
                            "Will be used by staff to update information or manually verify a user",
                            false
                    )
                    embed.addField("[WIP - ADMIN ONLY] >scheduleMsg <Message> <Timestamp>",
                            "Can be used to schedule an announcement for a particular time.",
                            false
                    )
                    channel.sendMessageEmbeds(embed.build()).queue()
                    activityLog.sendActivityMsg("[MAIN] help command triggered!", 1)
                } else if (msgContents.equals(">verifyinfo")) {
                    val embed = EmbedBuilder()
                    embed.setTitle("User lookup: ")
                    try {
                        activityLog.sendActivityMsg("[MAIN] User lookup command triggered", 1)
                        val id: String = msg.getAuthor().getId()
                        embed.setDescription("This command has returned **all** information associated with your account that was collected during the verification process.")
                        if (db.getDBEntry("CERT", id).equals("No results found")) {
                            embed.setColor(Color.RED)
                            embed.addField("Status:", "Your account has not been verified therefore there is no collected data associated with your discord id", false)
                        } else {
                            embed.setColor(Color.ORANGE)
                            embed.addField("Status:", db.getDBEntry("CERT", id), false)
                        }
                        embed.setFooter("Data sourced from Aria's internal database")
                    } catch (e: Exception) {
                        System.out.println("Long failed")
                        embed.setDescription("**Lookup failed, please try again later")
                        embed.setFooter("data sourced from internal database")
                    }
                    msg.getAuthor().openPrivateChannel().flatMap { verifyinfoch -> verifyinfoch.sendMessageEmbeds(embed.build()) }.queue()
                    channel.sendMessage(user.getAsMention() + " , Please check your DMs, you should receive your verification data there.").queue()
                }
            }
        }

        // TODO: Move this to database class
        // for commands with params
        if (channel.getId().equals(constants.permittedChannels[1])) {
            if (msgContents.contains(">userLookup")) {
                System.out.println("Running userLookup cmd")
                // todo move this to a different class to prevent function envy
                val parsedContents: Array<String> = msgContents.split(" ")
                val embed = EmbedBuilder()
                embed.setTitle("User lookup: ")
                try {
                    Long.parseLong(parsedContents[1])
                    if (!msg.getMentions().getUsers().isEmpty()) {
                        val x: User = msg.getMentions().getUsers().get(0)
                        embed.setDescription(
                                "Results for: ${x.getId()}\n" +
                                "${db.getDBEntry("CERT", x.getId())}"
                        )
                    } else {
                        embed.setDescription(
                                "Results for: ${parsedContents[1]}\n" +
                                "${db.getDBEntry("CERT", parsedContents[1])}"
                        )
                    }
                    embed.setFooter("data sourced from internal database")
                } catch (e: Exception) {
                    System.out.println("Long failed")
                    activityLog.sendActivityMsg("[MAIN] " + e.getMessage(), 3)
                    embed.setDescription("**Lookup failed, please ensure you've correctly copied the discord ID**")
                    embed.setFooter("data sourced from internal database")
                }
                channel.sendMessageEmbeds(embed.build()).queue()
            } else if (msgContents.contains(">resetOnCampus")) {
                val x = OnCampus(true)
                activityLog.sendActivityMsg("[MAIN] resetOnCampus command has been activated!", 2)
                channel.sendMessage("On Campus feature has been successfully reset!")
            } else if (msgContents.contains(">serviceMode")) {
                val parsedContents: Array<String> = msgContents.split(" ")
                serviceMode = true
                val misc = Misc()
                val verify: MessageChannel = constants.jda.getTextChannelById(constants.permittedChannels[0])
                misc.sendServiceModeMsg(verify,
                        "Aria is currently in maintenance mode. " +
                        "The ability to verify has now been temporarily disabled, the estimated downtime will be ${parsedContents[1]}. " +
                        "Sorry for any inconvenience."
                )
                activityLog.sendActivityMsg("[MAIN] Service mode is now active", 2)
            } else if (msgContents.contains(">reactivate")) {
                val misc = Misc()
                val verify: MessageChannel = constants.jda.getTextChannelById(constants.permittedChannels[0])
                misc.sendServiceModeMsg(verify, "Aria has reactivated the ability to verify and has exited maintenance mode.")
                activityLog.sendActivityMsg("[MAIN] Aria bot has exited service mode", 2)
            } else if (msgContents.contains(">help")) {
                val embed = EmbedBuilder()
                embed.setColor(Color.MAGENTA)
                embed.setTitle("ADMIN Commands")
                embed.setDescription("Here are the following commands that you are able to use:")
                embed.addField(">userLookup <discordID>", "This command will lookup a user's verification status and other recorded details.", false)
                embed.addField(">reactivate", "Will re-enable the ability to verify and other parts of the bot that have been deactivated", false)
                embed.addField(">serviceMode <Time> E.g. 10mins", "Can be used to deactivate interruption sensitive parts of the bot, e.g. verify module", false)
                channel.sendMessageEmbeds(embed.build()).queue()
                activityLog.sendActivityMsg("[MAIN] Help command has been activated", 1)
            }
        }
    }

    companion object {
        @Throws(Exception::class)
        fun main(arguments: Array<String?>?) {
            var activity = "Routines!"

            // setters for various props
            val BOT_TOKEN: String = System.getenv("DISCORD_CLIENT_SECRET")
            if (Boolean.parseBoolean(System.getenv("IS_DEV"))) {
                activity = "Dev Build Active"
                constants.VERIFIED_ROLE_ID = "909827233194070039"
                constants.COVID_UPDATE_CHANNEL = "912726004886294569"
                constants.permittedChannels[0] = "912353229285765172"
                constants.permittedChannels[1] = "912353440749985852"
                constants.NEWS_CHANNEL = "957640138597490688"
                constants.EXPOSURE_SITE_CHANNEL = "912353229285765172"
                constants.ONCAMPUS_CHANNEL_ID = "960693585508982824"
                constants.ONCAMPUS_ROLE_ID = "960693586163269683"
                constants.ACTIVITY_LOG_ID = "981456425530298429"
            }
            val jda: JDA = JDABuilder.createLight(BOT_TOKEN, GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES)
                                     .addEventListeners(Main())
                                     .setActivity(Activity.playing(activity))
                                     .enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_MEMBERS)
                                     .setMemberCachePolicy(MemberCachePolicy.ALL)
                                     .build()
            jda.awaitReady()
            constants.jda = jda
            activityLog = ActivityLog()

            val close = Close()
            Runtime.getRuntime().addShutdownHook(close)
            activityLog.sendActivityMsg("[MAIN] Aria Bot is starting up...", 1)

            db = Database()
            News("Covid", db)
            News("Monash", db)
            News("ExposureBuilding", db)

            val x = OnCampus(false)
            activityLog.sendActivityMsg("[MAIN] Aria Bot has initialised successfully!", 1)
        }
    }
}