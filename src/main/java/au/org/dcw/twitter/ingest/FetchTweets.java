/*
 * Copyright 2017 Derek Weber
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.org.dcw.twitter.ingest;

import au.org.dcw.twitter.ingest.ui.FetchTweetUI;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.common.collect.Lists;
import twitter4j.RateLimitStatus;
import twitter4j.RateLimitStatusEvent;
import twitter4j.RateLimitStatusListener;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.TwitterObjectFactory;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;


/**
 * <p>This app can fetch the JSON for one or more tweets. It has a GUI mode, which
 * can be used to fetch one tweet at a time, or in a commandline mode where the IDs
 * of tweets can be provided on the commandline or in a file, one per line, while
 * output is written to <code>stdout</code>.</p>
 *
 * <p>Twitter credentials are looked for in "./twitter.properties", and proxy info
 * is looked for in "./proxy.properties". Commandline options for the input file,
 * the output file, and the Twitter properties are provided, along with a verbose
 * mode.</p>
 *
 * @author <a href="mailto:weber.dc@gmail.com">Derek Weber</a>
 */
class FetchTweets {

    /**
     * Set to max number of IDs accepted by https://api.twitter.com/1.1/statuses/lookup.json
     *
     * @see Twitter's <a href="https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/get-statuses-lookup">GET statuses/lookup</a>
     */
    private static final int REFETCH_BATCH_SIZE = 100;

    @Parameter(names = {"-i", "--id", "--ids"}, description = "ID of tweet(s) to fetch")
    private List<String> idStrs = Lists.newArrayList();

    @Parameter(names = {"-f", "--ids-file"}, description = "File of tweet IDs to fetch (one per line)")
    private String infile;

    @Parameter(names = {"-c", "--credentials"},
               description = "Properties file with Twitter OAuth credentials")
    private String credentialsFile = "./twitter.properties";

    @Parameter(names = {"-v", "--debug", "--verbose"}, description = "Debug mode")
    private boolean debug = false;

    @Parameter(names = {"-h", "-?", "--help"}, description = "Help")
    private static boolean help = false;

    public static void main(String[] args) throws IOException {
        FetchTweets theApp = new FetchTweets();

        // JCommander instance parses args, populates fields of theApp
        JCommander argsParser = JCommander.newBuilder()
            .addObject(theApp)
            .programName("bin/fetch-tweets[.bat]")
            .build();
        argsParser.parse(args);

        if (help) {
            StringBuilder sb = new StringBuilder();
            argsParser.usage(sb);
            System.out.println(sb.toString());
            System.exit(-1);
        }

        theApp.run();
    }

    private void run() throws IOException {

        // establish resources
        final Configuration twitterConfig = makeTwitterConfig(credentialsFile, debug);
        final Twitter twitter = new TwitterFactory(twitterConfig).getInstance();
        twitter.addRateLimitStatusListener(new RateLimitStatusListener() {
            @Override
            public void onRateLimitStatus(RateLimitStatusEvent event) {
                maybeDoze(event.getRateLimitStatus());
            }

            @Override
            public void onRateLimitReached(RateLimitStatusEvent event) {
                maybeDoze(event.getRateLimitStatus());
            }
        });

        if (inGuiMode()) {
            // Create and set up the window
            JFrame frame = new JFrame("Fetch Tweet");
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

            JComponent gui = new FetchTweetUI(twitter, debug);
            frame.setContentPane(gui);

            // Display the window
            frame.setSize(500, 700);
            frame.setVisible(true);

        } else {

            // read in tweet IDs
            final List<Long> tweetIDs = Lists.newArrayList();

            // specified on the commandline
            if (! idStrs.isEmpty()) {
                tweetIDs.addAll(idStrs.stream().map(Long::parseLong).collect(Collectors.toList()));
            }
            // referred to in a nearby file
            if (infile != null) {
                tweetIDs.addAll(Files
                    .lines(Paths.get(infile))
                    .filter(s -> ! s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList())
                );
            }

            // fetch in batches
            Lists.partition(tweetIDs, REFETCH_BATCH_SIZE).forEach( batchOfIDs -> {
                // prepare arguments for call to Twitter
                final long[] arrayOfIDs = new long[batchOfIDs.size()];
                for (int i = 0; i < batchOfIDs.size(); i++) {
                    arrayOfIDs[i] = batchOfIDs.get(i);
                }

                // hit Twitter's API
                ResponseList<Status> response = null;
                try {
                    response = twitter.lookup(arrayOfIDs);

                    response.forEach ( tweet -> {
                        // NB get Twitter's raw JSON, don't convert Twitter4J objs to JSON
                        // via Jackson (they different structures & field names)
                        String rawJSON = TwitterObjectFactory.getRawJSON(tweet);
                        System.out.println(rawJSON);
                    });
                } catch (TwitterException te) {
                    te.printStackTrace();
                    System.err.println("Failed somehow: " + te.getMessage());
                    System.err.println("Attempting to continue...");
                }
                if (response != null) {
                    // Respect Twitter's authoritay on rate limits
                    maybeDoze(response.getRateLimitStatus());
                }
            });
        }
    }

    /**
     * Says yes to GUI mode if no IDs are referred to on the commandline.
     *
     * @return True if the GUI should be launched.
     */
    private boolean inGuiMode() {
        return infile == null && idStrs.isEmpty();
    }

    /**
     * If the provided {@link RateLimitStatus} indicates that we are about to exceed the rate
     * limit, in terms of number of calls or time window, then sleep for the rest of the period.
     *
     * @param status The current rate limit status of our calls to Twitter
     */
    private void maybeDoze(final RateLimitStatus status) {
        if (status == null) { return; }

        final int secondsUntilReset = status.getSecondsUntilReset();
        final int callsRemaining = status.getRemaining();
        if (secondsUntilReset < 10 || callsRemaining < 10) {
            final int untilReset = status.getSecondsUntilReset() + 5;
            System.out.printf("Rate limit reached. Waiting %d seconds starting at %s...\n", untilReset, new Date());
            try {
                Thread.sleep(untilReset * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Resuming...");
        }
    }

    /**
     * Builds the {@link Configuration} object with which to connect to Twitter, including
     * credentials and proxy information if it's specified.
     *
     * @return a Twitter4j {@link Configuration} object
     * @throws IOException if there's an error loading the application's {@link #credentialsFile}.
     */
    private static Configuration makeTwitterConfig(
        final String credentialsFile,
        final boolean debug
    ) throws IOException {
        // TODO find a better name than credentials, given it might contain proxy info
        final Properties credentials = loadCredentials(credentialsFile);

        final ConfigurationBuilder conf = new ConfigurationBuilder();
        conf.setTweetModeExtended(true);
        conf.setJSONStoreEnabled(true)
            .setDebugEnabled(debug)
            .setOAuthConsumerKey(credentials.getProperty("oauth.consumerKey"))
            .setOAuthConsumerSecret(credentials.getProperty("oauth.consumerSecret"))
            .setOAuthAccessToken(credentials.getProperty("oauth.accessToken"))
            .setOAuthAccessTokenSecret(credentials.getProperty("oauth.accessTokenSecret"));

        final Properties proxies = loadProxyProperties();
        if (proxies.containsKey("http.proxyHost")) {
            conf.setHttpProxyHost(proxies.getProperty("http.proxyHost"))
                .setHttpProxyPort(Integer.parseInt(proxies.getProperty("http.proxyPort")))
                .setHttpProxyUser(proxies.getProperty("http.proxyUser"))
                .setHttpProxyPassword(proxies.getProperty("http.proxyPassword"));
        }

        return conf.build();
    }

    /**
     * Loads the given {@code credentialsFile} from disk.
     *
     * @param credentialsFile the properties file with the Twitter credentials in it
     * @return A {@link Properties} map with the contents of credentialsFile
     * @throws IOException if there's a problem reading the credentialsFile.
     */
    private static Properties loadCredentials(final String credentialsFile)
        throws IOException {
        final Properties properties = new Properties();
        properties.load(Files.newBufferedReader(Paths.get(credentialsFile)));
        return properties;
    }

    /**
     * Loads proxy information from <code>"./proxy.properties"</code> if it is
     * present. If a proxy host and username are specified by no password, the
     * user is asked to type it in via stdin.
     *
     * @return A {@link Properties} map with proxy credentials.
     */
    private static Properties loadProxyProperties() {
        final Properties properties = new Properties();
        final String proxyFile = "./proxy.properties";
        if (new File(proxyFile).exists()) {
            boolean success = true;
            try (Reader fileReader = Files.newBufferedReader(Paths.get(proxyFile))) {
                properties.load(fileReader);
            } catch (IOException e) {
                System.err.println("Attempted and failed to load " + proxyFile + ": " + e.getMessage());
                success = false;
            }
            if (success && !properties.containsKey("http.proxyPassword")) {
                char[] password = System.console().readPassword("Please type in your proxy password: ");
                properties.setProperty("http.proxyPassword", new String(password));
                properties.setProperty("https.proxyPassword", new String(password));
            }
            properties.forEach((k, v) -> System.setProperty(k.toString(), v.toString()));
        }
        return properties;
    }
}
