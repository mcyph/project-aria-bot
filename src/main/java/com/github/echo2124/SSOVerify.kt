package com.github.echo2124

import com.github.scribejava.core.builder.ScopeBuilder

class SSOVerify(user: User, guild: Guild, channel: MessageChannel, db: Database) : Thread() {
    private val user: User
    private val guild: Guild
    private val msgChannel: MessageChannel
    private val db: Database
    private var service: OAuth20Service? = null
    private var deviceAuthorization: DeviceAuthorization? = null
    private var intervalMillis: Long = 5000

    init {
        this.user = user
        this.guild = guild
        msgChannel = channel
        this.db = db
    }

    fun run() {
        System.out.println("[CERT MODULE] Thread #" + Thread.currentThread().getId() + " is active!")
        activityLog.sendActivityMsg("[VERIFY] Thread #" + Thread.currentThread().getId() + " is active!", 1)
        try {
            if (!Main.constants.serviceMode) {
                if (!checkVerification()) {
                    verify()
                } else {
                    sendPublicMsg()
                    sendMsg(user.getAsMention() + ", have already been verified! Aria.")
                }
            }
        } catch (e: Exception) {
            System.out.println(e.getMessage())
        }
    }

    fun timeout() {
        val task: TimerTask = object : TimerTask() {
            fun run() {
                if (!checkVerification()) {
                    sendFailureNotification("timeout")
                }
                try {
                    System.out.println("Put polling thread #${Thread.currentThread().getId()} into inactive state")
                    activityLog.sendActivityMsg("[VERIFY] Put polling thread #${Thread.currentThread().getId()} into inactive state", 1)
                    intervalMillis = (1800 * 1000).toLong()
                    System.out.println("Attempt to close #${Thread.currentThread().getId()}'s oauth service")
                    service.close()
                    activityLog.sendActivityMsg("[VERIFY] Attempt to close #${Thread.currentThread().getId()}'s oauth service", 1)
                } catch (e: Exception) {
                    System.out.println("Unable to close thread #${Thread.currentThread().getId()}'s oauth service")
                    activityLog.sendActivityMsg("[VERIFY] Unable to close thread #${Thread.currentThread().getId()}'s oauth service", 3)
                }
                System.out.println("[CERT MODULE] Thread #${Thread.currentThread().getId()} has stopped!")
                Thread.currentThread().interrupt()
                activityLog.sendActivityMsg("[VERIFY] [CERT MODULE] Thread #${Thread.currentThread().getId()} has stopped!", 1)
            }
        }
        val timer = Timer("Timer")
        // Equiv to 5mins and 10 secs.
        val delay = 306000L
        timer.schedule(task, delay)
    }

    fun checkVerification(): Boolean {
        var isVerified = false
        if (db.getDBEntry("CERT", user.getId()).contains("true")) {
            activityLog.sendActivityMsg("[VERIFY] User has already been verified!", 1)
            isVerified = true
        }
        return isVerified
    }

    fun sendMsg(msg: String?) {
        activityLog.sendActivityMsg("[VERIFY] Send private msg to user to indicate verification state", 1)
        user.openPrivateChannel().flatMap { channel ->
            channel.sendMessage(
                    msg
            )
        }.queue()
    }

    fun sendPublicMsg() {
        msgChannel.sendMessage("${user.getAsMention()} , Please check your DMs, you should receive the verification instructions there.").queue()
    }

    // TODO: Consider moving a lot of this text to a JSON object
    fun sendVerifiedNotification(name: String) {
        activityLog.sendActivityMsg("[VERIFY] Send verified notification via DMs", 1)
        val embed = EmbedBuilder()
        embed.setTitle("Verified!")
        embed.setColor(Color.green)
        embed.setDescription(
                "Hi $name,\n you have been successfully verified, you can now access channels " +
                        "that are exclusive for verified Monash University students only. \n" +
                " Thanks for verifying, Aria"
        )
        embed.setFooter("If you have any problems please contact Echo2124#3778 (creator of Aria)")
        user.openPrivateChannel().flatMap { channel -> channel.sendMessageEmbeds(embed.build()) }.queue()
    }

    fun sendFailureNotification(type: String?) {
        val embed = EmbedBuilder()
        embed.setColor(Color.red)
        when (type) {
            "invalid_account" -> {
                embed.setTitle("Invalid Google Account")
                embed.setDescription(
                        "Aria was unable to verify you. " +
                        "Please ensure that you are using a Monash Google Account, it should have an email that ends in @student.monash.edu.au . " +
                        "If the issues persist please contact Echo2124#3778 with a screenshot and description of the issue that you are experiencing. \n " +
                        "Best Regards, Aria. "
                )
                activityLog.sendActivityMsg("[VERIFY] REASON: Unable to verify user due to invalid google account", 1)
            }
            "invalid_name" -> {
                embed.setTitle("Invalid First Name")
                embed.setDescription(
                        "Your profile name too large, therefore verification has failed. " +
                        "You can change your first name in the Google Account settings. " +
                        "Please ensure that your account firstname is under 2048 characters."
                )
                activityLog.sendActivityMsg("[VERIFY] REASON: Unable to verify user due to invalid profile name", 1)
            }
            "timeout" -> {
                embed.setTitle("Verification timeout")
                embed.setDescription(
                        "Aria has noticed that the provided token was not used within the allocated timeframe. " +
                        "This is likely because you might of not followed the aforementioned steps. " +
                        "Please try to generate a new token by typing >verify at the specified verification channel on the IT @ Monash server."
                )
                activityLog.sendActivityMsg("[VERIFY] REASON: User did not verify in time", 1)
            }
        }
        activityLog.sendActivityMsg("[VERIFY] Send failure notification via DMs", 1)
        user.openPrivateChannel().flatMap { channel -> channel.sendMessageEmbeds(embed.build()) }.queue()
    }

    fun sendAuthRequest(link: String?, code: String?) {
        val authEmbed = EmbedBuilder()
        val faqEmbed = EmbedBuilder()
        faqEmbed.setColor(Color.BLUE)
        faqEmbed.setTitle("Frequently Asked Questions (FAQs)")
        faqEmbed.addField("What does this do?",
                "This OAuth request will ask access for two main scopes (Email & Profile).",
                false
        )
        faqEmbed.addField("What information will this Aria store?",
                "Aria will store the following information: Email Address, First Name, DiscordID, Time of Verification and Verification Status.",
                false
        )
        faqEmbed.addField("Why do we need this data?",
                "In order to verify whether you are a Monash student we need to check the Email Domain " +
                    "in order to see if it would match a student's Monash email domain. " +
                "If it does, then you are likely a student. " +
                "We store your first name, as Aria will be able to refer to you in a more personalised manner. " +
                "This name will only be used when Aria sends you a private message",
                false
        )
        authEmbed.setColor(Color.YELLOW)
        authEmbed.setTitle("Authorisation Request")
        authEmbed.setDescription(
                "Steps to verify yourself:\n" +
                " **1)**  Open provided link in your browser. \n" +
                " **2)** Paste provided code into input. \n" +
                " **3)** Select your Monash Google Account. \n" +
                " **4)** Done!"
        )
        authEmbed.addField("Link: ", link, false)
        authEmbed.addField("Code: ", code, false)
        authEmbed.setFooter("This access token will expire in **5 Mins!**")
        activityLog.sendActivityMsg("[VERIFY] Send FAQ & Auth request message via DMs", 1)
        user.openPrivateChannel().flatMap { channel -> channel.sendMessageEmbeds(faqEmbed.build()) }.queue()
        user.openPrivateChannel().flatMap { channel -> channel.sendMessageEmbeds(authEmbed.build()) }.queue()
    }

    @Throws(IOException::class, InterruptedException::class, ExecutionException::class)
    fun verify() {
        val clientId: String = System.getenv("GOOGLE_SSO_CLIENT_ID")
        val clientSecret: String = System.getenv("GOOGLE_SSO_CLIENT_SECRET")
        service = ServiceBuilder(clientId)
                .debug()
                .apiSecret(clientSecret)
                .defaultScope(ScopeBuilder("profile", "email")) // replace with desired scope
                .build(GoogleApi20.instance())
        System.out.println("Requesting a set of verification codes...")
        deviceAuthorization = service.getDeviceAuthorizationCodes()
        sendPublicMsg()
        timeout()
        sendAuthRequest(deviceAuthorization.getVerificationUri(), deviceAuthorization.getUserCode())
        if (deviceAuthorization.getVerificationUriComplete() != null) {
            System.out.println("Or visit " + deviceAuthorization.getVerificationUriComplete())
        }
        val accessToken: OAuth2AccessToken = pollAccessToken(deviceAuthorization)
        val requestUrl: String
        requestUrl = PROTECTED_RESOURCE_URL
        val request = OAuthRequest(Verb.GET, requestUrl)
        service.signRequest(accessToken, request)
        service.execute(request).use { response ->
            val parsedObj = JSONObject(response.getBody())
            if (verifyEmail(parsedObj) == true) {
                /// insert into db > add role > notify user
                if (parsedObj.getString("given_name").length() <= MAX_NAME_LEN) {
                    addVerifiedRole()
                    val parsedData: HashMap<String, String> = HashMap<String, String>()
                    parsedData.put("discordID", user.getId())
                    parsedData.put("name", parsedObj.getString("given_name"))
                    parsedData.put("emailAddr", parsedObj.getString("email"))
                    parsedData.put("isVerified", "true")
                    db.modifyDB("CERT", "add", parsedData)
                    sendVerifiedNotification(parsedObj.getString("given_name"))
                } else {
                    sendFailureNotification("invalid_name")
                }
            } else {
                sendFailureNotification("invalid_account")
            }
        }
    }

    fun verifyEmail(obj: JSONObject): Boolean {
        var isValid = false
        if (obj.has("hd")) {
            if (obj.getString("hd").equals("student.monash.edu") || obj.getString("hd").equals("monash.edu")) {
                isValid = true
                activityLog.sendActivityMsg("[VERIFY] Email matches a Monash University domain", 1)
            }
        }
        return isValid
    }

    fun addVerifiedRole() {
        try {
            guild.addRoleToMember(
                UserSnowflake.fromId(user.getIdLong()),
                guild.getRoleById(Main.constants.VERIFIED_ROLE_ID)
            ).queue()
            activityLog.sendActivityMsg("[VERIFY] Gave user (${user.getAsTag()}) verified role", 1)
            System.out.println("[VERBOSE] Added role")
        } catch (e: Exception) {
            System.out.println(e.getMessage())
            System.out.println("[ERROR] Probably a permission issue")
        }
    }

    // Custom implementation of token polling
    @Throws(InterruptedException::class, ExecutionException::class, IOException::class)
    fun pollAccessToken(deviceAuthorization: DeviceAuthorization?): OAuth2AccessToken {
        while (true) {
            try {
                return service.getAccessTokenDeviceAuthorizationGrant(deviceAuthorization)
            } catch (e: OAuth2AccessTokenErrorResponse) {
                if (e.getError() !== OAuth2Error.AUTHORIZATION_PENDING) {
                    intervalMillis += if (e.getError() === OAuth2Error.SLOW_DOWN) {
                        5000
                    } else {
                        throw e
                    }
                }
            }
            Thread.sleep(intervalMillis)
        }
    }

    companion object {
        private const val NETWORK_NAME = "Google"
        private const val PROTECTED_RESOURCE_URL = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val MAX_NAME_LEN = 2048
    }
}