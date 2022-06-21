package com.github.echo2124

import com.rometools.rome.feed.synd.SyndFeed

class News(newsType: String, db: Database) {
    private var cachedTitle = ""
    private var feedOrg: String? = null

    // fallback if author is not available from rss feed
    private val defaultAuthors = arrayOf("Monash University", "ABC News")
    private val monashCategories = arrayOf("Technology Related News", "COVID-19 Related News", "General University News")
    private var feed: SyndFeed? = null
    private val feedIndex = 0
    private val targetedExposureBuildingUrl = "https://www.monash.edu/news/coronavirus-updates/exposure-sites"
    private val db: Database

    // if category not exist, push regardless, if category check for title. Match against feed title trying to be pushed
    init {
        this.db = db
        if (newsType.equals("Covid")) {
            feedOrg = "ABC"
            if (!Boolean.parseBoolean(System.getenv("IS_DEV"))) {
                latestTweet
            }
        } else if (newsType.equals("Monash")) {
            feedOrg = "Monash"
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "technology"))) {
                System.out.println("[News] Technology Category Found!")
                initRSS("https://www.monash.edu/_webservices/news/rss?category=engineering+%26+technology", "technology", true)
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?category=engineering+%26+technology", "technology", false)
            }
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "covid"))) {
                System.out.println("[News] COVID Category Found!")
                initRSS("https://www.monash.edu/_webservices/news/rss?query=covid", "covid", true)
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?query=covid", "covid", false)
            }
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "news"))) {
                System.out.println("[News] News Category Found!")
                initRSS("https://www.monash.edu/_webservices/news/rss?category=university+%26+news", "news", true)
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?category=university+%26+news", "news", false)
            }
            setInterval()
        } else if (newsType.equals("ExposureBuilding")) {
            try {
                System.out.println("[NEWS] Getting Exposure Building info")
                val doc: Document = Jsoup.connect(targetedExposureBuildingUrl).get()
                System.out.println(doc.title())
                fetchCovidExposureInfo(doc)
            } catch (e: Exception) {
                System.out.println("[Exposure Site] ERROR: " + e.getMessage())
                activityLog.sendActivityMsg("[NEWS] Unable to get exposure info: " + e.getMessage(), 3)
            }
        }
    }

    // checks  every 4 hrs for RSS feed updates
    fun setInterval() {
        val updateMonashNews: TimerTask = object : TimerTask() {
            fun run() {
                News("Monash", Main.constants.db)
                News("ExposureBuilding", Main.constants.db)
            }
        }
        val timer = Timer("Timer")
        val delay = 2.16e7.toInt().toLong()
        timer.schedule(updateMonashNews, delay)
    }

    val latestTweet: Unit
        get() {
            val cb = ConfigurationBuilder()
            cb.setDebugEnabled(true)
                    .setOAuthConsumerKey(System.getenv("TWITTER_CONSUMER_KEY"))
                    .setOAuthConsumerSecret(System.getenv("TWITTER_CONSUMER_SECRET"))
                    .setOAuthAccessToken(System.getenv("TWITTER_ACCESS_TOKEN"))
                    .setOAuthAccessTokenSecret(System.getenv("TWITTER_ACCESS_SECRET"))
            val ts: TwitterStream = TwitterStreamFactory(cb.build()).getInstance()
            val listener: StatusListener = object : StatusListener() {
                @Override
                fun onStatus(status: Status) {
                    if (status.getUser().getId() === 43064490) {
                        if (status.getText().contains("#COVID19VicData") || status.getText().contains("More data soon")) {
                            activityLog.sendActivityMsg("[NEWS] Building covid update msg", 1)
                            buildMsgFromTweet(status, "covid_update")
                        }
                    }
                }

                @Override
                fun onDeletionNotice(statusDeletionNotice: StatusDeletionNotice?) {
                }

                @Override
                fun onTrackLimitationNotice(numberOfLimitedStatuses: Int) {
                }

                @Override
                fun onScrubGeo(userId: Long, upToStatusId: Long) {
                }

                @Override
                fun onStallWarning(warning: StallWarning?) {
                }

                @Override
                fun onException(ex: Exception?) {
                }
            }
            ts.addListener(listener)
            ts.sample()
            val filter = FilterQuery()
            filter.follow(longArrayOf(43064490))
            ts.filter(filter)
        }

    fun initRSS(feedURL: String?, category: String, checkLatest: Boolean?) {
        try {
            activityLog.sendActivityMsg("[NEWS] Initialising RSS Feed listener for category: $category", 1)
            parseRSS(feedURL)
            sendMsg(feed, category, checkLatest)
        } catch (e: Exception) {
            activityLog.sendActivityMsg("[NEWS] Unable to initialise RSS Feed listener: " + e.getMessage(), 3)
            throw Error(e)
        }
    }

    @Throws(MalformedURLException::class)
    fun parseRSS(feedURL: String?) {
        activityLog.sendActivityMsg("[NEWS] Parsing received RSS Feed", 1)
        val newURL = URL(feedURL)
        var feed: SyndFeed? = null
        try {
            feed = SyndFeedInput().build(XmlReader(newURL))
        } catch (e: Exception) {
            activityLog.sendActivityMsg("[NEWS] Unable to parse RSS Feed: " + e.getMessage(), 3)
        }
        this.feed = feed
    }

    // compares prev posted msg to current to see if there's an update
    fun pushNewMsg(feed: SyndFeed) {
        // compare this to cached prev feed. If different then push update
        System.out.println(feed.getEntries().get(feedIndex))
        if (cachedTitle === "") {
            cachedTitle = feed.getEntries().get(feedIndex).getTitle()
        } else {
            if (cachedTitle.equals(feed.getEntries().get(feedIndex).getTitle())) {
                System.out.println("[News] Up To Date!")
            } else {
            }
        }
    }

    fun sendMsg(feed: SyndFeed?, category: String, checkState: Boolean?) {
        if (!checkState!! || !Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_LASTITLE", category + "##" + feed.getEntries().get(feedIndex).getTitle()))) {
            val channel: MessageChannel = Main.constants.jda.getTextChannelById(Main.constants.NEWS_CHANNEL)
            val newEmbed = EmbedBuilder()
            if (feed.getEntries().get(feedIndex).getAuthor().equals("") || feed.getAuthor() == null) {
                if (feedOrg!!.equals("Monash")) {
                    newEmbed.setAuthor(defaultAuthors[0])
                }
            } else {
                newEmbed.setAuthor(feed.getEntries().get(feedIndex).getAuthor())
            }
            newEmbed.setTitle(feed.getEntries().get(feedIndex).getTitle(), feed.getEntries().get(feedIndex).getLink())
            newEmbed.setDescription(feed.getEntries().get(feedIndex).getDescription().getValue())
            if (!feed.getEntries().get(feedIndex).getEnclosures().isEmpty()) {
                newEmbed.setImage(feed.getEntries().get(feedIndex).getEnclosures().get(0).getUrl())
            }
            newEmbed.setThumbnail(feed.getImage().getUrl())
            when (category) {
                "technology" -> newEmbed.setFooter(monashCategories[0])
                "covid" -> newEmbed.setFooter(monashCategories[1])
                "news" -> newEmbed.setFooter(monashCategories[2])
                else -> newEmbed.setFooter(feed.getDescription())
            }
            channel.sendMessageEmbeds(newEmbed.build()).queue()
            activityLog.sendActivityMsg("[NEWS] Sending Monash News update", 1)
            val data: HashMap<String, String> = HashMap<String, String>()
            data.put("title", feed.getEntries().get(feedIndex).getTitle())
            db.modifyDB("NEWS", category, data)
        }
    }

    fun buildMsgFromTweet(status: Status, type: String) {
        System.out.println("Building MSG From tweet")
        var channel: MessageChannel? = null
        if (type.equals("covid_update")) {
            channel = Main.constants.jda.getTextChannelById(Main.constants.COVID_UPDATE_CHANNEL)
        }
        val newEmbed = EmbedBuilder()
        newEmbed.setTitle("Victoria Covid Update")
        newEmbed.setDescription(status.getText())
        newEmbed.setAuthor("Victorian Department of Health")
        newEmbed.setThumbnail(status.getUser().getProfileImageURL())
        val media: Array<MediaEntity> = status.getMediaEntities()
        if (media.size > 0) {
            if (media[0].getMediaURL().contains("twimg")) {
                System.out.println("Media detected")
                newEmbed.setImage(media[0].getMediaURL())
            }
        }
        newEmbed.setFooter(status.getUser().getDescription())
        channel.sendMessageEmbeds(newEmbed.build()).queue()
    }

    fun fetchCovidExposureInfo(doc: Document) {
        activityLog.sendActivityMsg("[NEWS] Fetching exposure info from remote", 1)
        val jsonParentObject = JSONObject()
        var numExposures = 0
        try {
            val table: Element = doc.select("#covid-19_exposure_site__table").get(0)
            System.out.println("[NEWS] Parsing exposure site data")
            for (row in table.select("tr")) {
                val jsonObject = JSONObject()
                val tds: Elements = row.select("td")
                if (!tds.isEmpty()) {
                    val campus: String = tds.get(0).text()
                    val building: String = tds.get(1).text()
                    val exposurePeriod: String = tds.get(2).text()
                    val cleaningStatus: String = tds.get(3).text()
                    val healthAdvice: String = tds.get(4).text()
                    jsonObject.put("Campus", campus)
                    jsonObject.put("Building", building)
                    jsonObject.put("ExposurePeriod", exposurePeriod)
                    jsonObject.put("CleaningStatus", cleaningStatus)
                    jsonObject.put("HealthAdvice", healthAdvice)
                    jsonParentObject.put(String.valueOf(numExposures), jsonObject)
                    numExposures++
                }
            }
        } catch (e: Exception) {
            System.out.println("[NEWS] ERROR: unable to parse exposure site table")
            activityLog.sendActivityMsg("[NEWS] ERROR: unable to parse exposure site table", 3)
        }
        System.out.println("JSON:")
        System.out.println(jsonParentObject.toString())
        var retrivedIndex: Int = Integer.parseInt(db.getDBEntry("CHECK_EXPOSURE_INDEX", "EXPOSURE_SITE"))
        if (retrivedIndex == 0) {
            retrivedIndex = numExposures - 4
        }
        if (numExposures > retrivedIndex) {
            // do quick math here, find difference and reverse json object possibly
            val data: HashMap<String, String> = HashMap<String, String>()
            data.put("col_name", "exposure_sites")
            data.put("size", String.valueOf(numExposures))
            db.modifyDB("EXPOSURE_SITE", "", data)
            for (i in 0 until numExposures - retrivedIndex) {
                buildMsgFromWebScrape(jsonParentObject.getJSONObject(String.valueOf(i)))
            }
        }
    }

    fun buildMsgFromWebScrape(data: JSONObject) {
        activityLog.sendActivityMsg("[NEWS] Building exposure message", 1)
        val channel: MessageChannel = Main.constants.jda.getTextChannelById(Main.constants.EXPOSURE_SITE_CHANNEL)
        val embed = EmbedBuilder()
        embed.setTitle("Exposure Sites Update")
        // will be the contents of above method **if** there is an update
        embed.addField("Campus: ", data.getString("Campus"), false)
        embed.addField("Building: ", data.getString("Building"), false)
        embed.addField("Exposure Period: ", data.getString("ExposurePeriod"), false)
        embed.addField("Cleaning Status: ", data.getString("CleaningStatus"), false)
        embed.addField("Health Advice: ", data.getString("HealthAdvice"), false)
        embed.setDescription("As always if you test positive to covid and have been on campus please report it to Monash University using the button below.")
        embed.setAuthor("Monash University")
        embed.setThumbnail("http://www.monash.edu/__data/assets/image/0008/492389/monash-logo.png")
        val btns: ArrayList<Button> = ArrayList<Button>()
        btns.add(Button.link("https://www.monash.edu/news/coronavirus-updates", "Monash COVID Bulletin").withEmoji(Emoji.fromUnicode("U+2139")))
        btns.add(Button.link("https://forms.monash.edu/covid19-self-reporting", "Monash COVID Self-Report").withEmoji(Emoji.fromUnicode("U+1F4DD")))
        channel.sendMessageEmbeds(embed.build()).setActionRow(btns).queue()
    }
}