package com.github.echo2124;


import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;
import twitter4j.json.DataObjectFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLOutput;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class News {

    private String cachedTitle="";
    private String feedOrg;
    // fallback if author is not available from rss feed
    private String[] defaultAuthors= {"Monash University", "ABC News"};
    private SyndFeed feed;
    private final int feedIndex =0;
    private final String targetedExposureBuildingUrl="https://www.monash.edu/news/coronavirus-updates/exposure-sites";
    private Database db;
    // if category not exist, push regardless, if category check for title. Match against feed title trying to be pushed
    public News(String newsType, Database db) {
        this.db=db;
        if (newsType.equals("Covid")) {
            feedOrg = "ABC";
            if (!Boolean.parseBoolean(System.getenv("IS_DEV"))) {
                getLatestTweet();
            }
        } else if (newsType.equals("Monash")) {
            feedOrg = "Monash";
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "technology"))) {
                System.out.println("[News] Technology Category Found!");
                initRSS("https://www.monash.edu/_webservices/news/rss?category=engineering+%26+technology", "technology", true);
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?category=engineering+%26+technology", "technology", false);
            }
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "covid"))) {
                System.out.println("[News] COVID Category Found!");
                initRSS("https://www.monash.edu/_webservices/news/rss?query=covid", "covid", true);
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?query=covid", "covid", false);
            }
            if (Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_CATEGORY", "news"))) {
                System.out.println("[News] News Category Found!");
                initRSS("https://www.monash.edu/_webservices/news/rss?category=university+%26+news", "news", true);
            } else {
                initRSS("https://www.monash.edu/_webservices/news/rss?category=university+%26+news", "news", false);
            }
            setInterval();
        } else if (newsType.equals("ExposureBuilding")) {
            try {
                System.out.println("[NEWS] Getting Exposure Building info");
                Document doc = Jsoup.connect(targetedExposureBuildingUrl).get();
                System.out.println(doc.title());
                fetchCovidExposureInfo(doc);
            } catch (Exception e) {
                System.out.println("[Exposure Site] ERROR: "+e.getMessage());
            }
        }
    }


    // checks  every 4 hrs for RSS feed updates
    public void setInterval() {
        TimerTask updateMonashNews = new TimerTask() {
            public void run() {
                new News("Monash", Main.constants.db);
            //    sendTestingMsg();
            }
        };
        Timer timer = new Timer("Timer");
        long delay=(int) 2.16e7;
        timer.schedule(updateMonashNews, delay);

    }

    public void sendTestingMsg() {
        MessageChannel channel = Main.constants.jda.getTextChannelById(Main.constants.NEWS_CHANNEL);
        channel.sendMessage("Polling successful, checked for rss feed article updates").queue();
    }

    public void getLatestTweet() {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey("tu7AA1josocRsfFp2sgLriVGA")
                .setOAuthConsumerSecret("qYKut6rMwPleyhjHF03NJU4YdUQe8vInmDFa3cCOJUXW055ru8")
                .setOAuthAccessToken("786025827280392192-OuNhuZIPYb5N4yS667I72HXZMyGSmNH")
                .setOAuthAccessTokenSecret("ujI55HDOu1ZLlKDdiO2TzhSEwZgsaGLsfb0ztAqSt2EFW");
        TwitterStream ts = new TwitterStreamFactory(cb.build()).getInstance();
        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                if (status.getUser().getId()==43064490){
                   if (status.getText().contains("#COVID19VicData") || status.getText().contains("More data soon")) {
                       buildMsgFromTweet(status, "covid_update");
                   }
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {

            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {

            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {

            }

            @Override
            public void onStallWarning(StallWarning warning) {

            }

            @Override
            public void onException(Exception ex) {

            }
        };
        ts.addListener(listener);
        ts.sample();
      /*  Twitter twitter = new TwitterFactory(cb.build()).getInstance();
        List<Status> statuses;
        try {
            statuses = twitter.getUserTimeline(43064490);
            Status newStatus = statuses.get(1);
            buildMsgFromTweet(newStatus);
        } catch (Exception e) {
        }*/



        FilterQuery filter = new FilterQuery();
        filter.follow(new long[] {43064490});
        ts.filter(filter);

    }



    public void initRSS(String feedURL, String category, Boolean checkLatest) {
        try {
            parseRSS(feedURL);
           // buildMSG(this.feed);
            sendMsg(this.feed,category,checkLatest);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public void parseRSS(String feedURL) throws MalformedURLException {
        URL newURL = new URL(feedURL);
        SyndFeed feed=null;
        try {
            feed = new SyndFeedInput().build(new XmlReader(newURL));
        } catch (Exception e) {
        }
        this.feed=feed;
    }

    // compares prev posted msg to current to see if there's an update
    public void pushNewMsg(SyndFeed feed) {
        // compare this to cached prev feed. If different then push update
        System.out.println(feed.getEntries().get(feedIndex));
        if (cachedTitle == "") {
            cachedTitle = feed.getEntries().get(feedIndex).getTitle();
        } else {
            if (cachedTitle.equals(feed.getEntries().get(feedIndex).getTitle())) {
                System.out.println("[News] Up To Date!");
            } else {

            }
        }
    }

    public void sendMsg(SyndFeed feed, String category, Boolean checkState) {
        if (!checkState || !Boolean.parseBoolean(db.getDBEntry("NEWS_CHECK_LASTITLE",category+"##"+feed.getEntries().get(feedIndex).getTitle()))) {
            MessageChannel channel = Main.constants.jda.getTextChannelById(Main.constants.NEWS_CHANNEL);
            EmbedBuilder newEmbed = new EmbedBuilder();
            if (feed.getEntries().get(feedIndex).getAuthor().equals("") || feed.getAuthor() == null) {
                if (feedOrg.equals("Monash")) {
                    newEmbed.setAuthor(defaultAuthors[0]);
                }
            } else {
                newEmbed.setAuthor(feed.getEntries().get(feedIndex).getAuthor());
            }
            newEmbed.setTitle(feed.getEntries().get(feedIndex).getTitle(), feed.getEntries().get(feedIndex).getLink());
            newEmbed.setDescription(feed.getEntries().get(feedIndex).getDescription().getValue());
            if (!feed.getEntries().get(feedIndex).getEnclosures().isEmpty()) {
                newEmbed.setImage(feed.getEntries().get(feedIndex).getEnclosures().get(0).getUrl());
            }
            newEmbed.setThumbnail(feed.getImage().getUrl());
            newEmbed.setFooter(feed.getDescription());
            channel.sendMessage(newEmbed.build()).queue();
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("title", feed.getEntries().get(feedIndex).getTitle());
            db.modifyDB("NEWS", category,data);
        }
    }


    public void buildMsgFromTweet(Status status, String type) {
        System.out.println("Building MSG From tweet");
        MessageChannel channel =null;
        if (type.equals("covid_update")) {
            channel = Main.constants.jda.getTextChannelById(Main.constants.COVID_UPDATE_CHANNEL);
        } else {
           // channel = Main.constants.jda.getTextChannelById(Main.constants.NEWS_CHANNEL);
        }
        EmbedBuilder newEmbed = new EmbedBuilder();
        newEmbed.setTitle("Victoria Covid Update");
        newEmbed.setDescription(status.getText());
        newEmbed.setAuthor("Victorian Department of Health");
        newEmbed.setThumbnail(status.getUser().getProfileImageURL());
        MediaEntity[] media = status.getMediaEntities();
        if (media.length >0) {
            if (media[0].getMediaURL().contains("twimg")) {
                System.out.println("Media detected");
                newEmbed.setImage(media[0].getMediaURL());
            }
        }
        newEmbed.setFooter(status.getUser().getDescription());
        channel.sendMessage(newEmbed.build()).queue();
    }

    public void fetchCovidExposureInfo(Document doc) {
        JSONObject jsonParentObject = new JSONObject();
        int numExposures = 0;
        //JSONArray list = new JSONArray();
       Element table = doc.select("#covid-19_exposure_site__table").get(0);
        System.out.println("[NEWS] Parsing exposure site data");
            for (Element row : table.select("tr")) {
                JSONObject jsonObject = new JSONObject();
                Elements tds = row.select("td");
                if (!tds.isEmpty()) {
                    String campus = tds.get(0).text();
                    String building = tds.get(1).text();
                    String exposurePeriod = tds.get(2).text();
                    String cleaningStatus = tds.get(3).text();
                    String healthAdvice = tds.get(4).text();
                    jsonObject.put("Campus", campus);
                    jsonObject.put("Building", building);
                    jsonObject.put("ExposurePeriod", exposurePeriod);
                    jsonObject.put("CleaningStatus", cleaningStatus);
                    jsonObject.put("HealthAdvice", healthAdvice);
                    jsonParentObject.put(String.valueOf(numExposures), jsonObject);
                    numExposures++;
                }
        }
        System.out.println("JSON:");
        System.out.println(jsonParentObject.toString());
        int retrivedIndex=Integer.parseInt(db.getDBEntry("CHECK_EXPOSURE_INDEX", "EXPOSURE_SITE"));
        if (numExposures>retrivedIndex) {
            // do quick math here, find difference and reverse json object possibly
            HashMap<String, String> data = new HashMap<String, String>();
            data.put("col_name", "exposure_sites");
            data.put("size", String.valueOf(numExposures));
            db.modifyDB("EXPOSURE_SITE","", data);
            for (int i=0; i<(numExposures-retrivedIndex)-1;i++) {
                buildMsgFromWebScrape(jsonParentObject.getJSONObject(String.valueOf(i)));
            }

        }
        // check if there are new exposure sites. Use index, e.g. if there is 25 stored in db, and update happens and there is 27, then generate messages for the last two.
    }

    public void buildMsgFromWebScrape(JSONObject data) {
        MessageChannel channel = Main.constants.jda.getTextChannelById(Main.constants.EXPOSURE_SITE_CHANNEL);
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Exposure Sites Update!");
        // will be the contents of above method **if** there is an update
        embed.setDescription(
                "Campus: "+data.getString("Campus")+
                        "\nBuilding: "+data.getString("Building")+
                        "\nExposure Period: "+ data.getString("ExposurePeriod")+
                        "\nCleaning Status: "+ data.getString("CleaningStatus")+
                        "\nHealth Advice: "+data.getString("HealthAdvice")
        );
        embed.setAuthor("Monash University");

    }
}
