# fetch-tweets

Author: **Derek Weber**

Last updated: **2017-11-03**

A tool to fetch tweets, with commandline and GUI modes.


## Description

This app will look for tweet IDs on the commandline, or from a file specified on
the commandline, or will launch a GUI into which can be pasted individual tweet IDs
or URLs, which can then be fetched. In command line mode, the JSON for the retrieved
tweets is sent to `stdout`.

### Extra: Sanitising the tweet

An extra feature, which has been better housed in another [project](https://github.com/weberdc/tweet-sanitiser.git)
remains in this tool, and it is the ability to sanitise the collected or provided
tweet JSON, leaving only specified fields. This functionality is hidden within the
main GUI via a closed split pane (expandable from the bottom of the GUI).

In GUI mode, the raw JSON is provided along with a version stripped of many of the
fields to make it easier to avoid collecting sensitive information. The fields can be
specified with the `-k` or `--keep-file` commandline option. An example input file
can be found in `data/test/properties-to-keep.txt`. Properties are comma- or 
space-separated if they're on the same line or can be specified one per line. If 
fields are not specified this way, the ones retained by default are:

 + `id` and `id_str`
 + `created_at`
 + `text` and `full_text` and `extended_tweet.full_text`
 + `user.screen_name`
 + `coordinates`
 + `place`
 + `entities.media` (this can also be removed by using the provided checkbox)
 
There is an editable text field to tweak the properties to keep in the UI also,
which will dynamically update the stripped JSON field.

**NB** Field stripping is not provided in the commandline version.

As of [2017-09-27](https://developer.twitter.com/en/docs/tweets/tweet-updates),
Twitter is progressively rolling out 280 character tweets, referred to as 
"extended tweets", and the "text" field is replaced by "full_text". In November
2017, Twitter [announced](https://blog.twitter.com/official/en_us/topics/product/2017/tweetingmadeeasier.html)
the 280 character limit would be extended (haha) to all Twitter users who needed
it (some languages are more concise than others). I am using Twitter4J in
_extended_ mode, but as a courtesy to those still running on standard mode, my
'stripped' objects will have "full_text" copied to "text", if there is no content
there already. The full raw JSON will be whatever Twitter supplies in 'extended' mode.

The text area at the top (which holds the full JSON of a Tweet) has a "Paste from
clipboard" button. With this, if the user copies the JSON for a tweet from 
somewhere else (e.g. a previous collection), the app will paste it into the top
text area, and the lower text area will contained an appropriately filtered version 
of the top one. Clicking in the text area and using Ctrl-V would not work, because
as soon as the user clicks in the area, the text will be pushed to the clipboard -
a feature for extracting the JSON quickly.

Twitter credentials are looked for in `"./twitter.properties"`, and proxy info
is looked for in `"./proxy.properties"`. Commandline options for the input file,
the output file, and the Twitter properties are provided, along with a verbose
mode.


## Requirements:

 + Java Development Kit 1.8
 + [Twitter4J](http://twitter4j.org) (Apache 2.0 licence)
   + depends on [JSON](http://json.org) ([JSON licence](http://www.json.org/license.html))
 + [Google Guava](https://github.com/google/guava) (Apache 2.0 licence) 
 + [FasterXML](http://wiki.fasterxml.com/JacksonHome) (Apache 2.0 licence)
 + [jcommander](http://jcommander.org) (Apache 2.0 licence)

Built with [Gradle 4.3](http://gradle.org), included via the wrapper.


## To Build

The Gradle wrapper has been included, so it's not necessary for you to have
Gradle installed - it will install itself as part of the build process. All that
is required is the Java Development Kit.

By running

`$ ./gradlew installDist` or `$ gradlew.bat installDist`

you will create an installable copy of the app in `PROJECT_ROOT/build/fetch-tweets`.

Use the target `distZip` to make a distribution in `PROJECT_ROOT/build/distributions`
or the target `timestampedDistZip` to add a timestamp to the distribution archive
filename.

To also include your own local `twitter.properties` file with your Twitter
credentials, use the target `privilegedDistZip` to make a special distribution
in `PROJECT_ROOT/build/distributions` that starts with.


## Configuration

Twitter OAuth credentials must be available in a properties file based on the
provided `twitter.properties-template` in the project's root directory. Copy the
template file to a properties file (the default is `twitter.properties` in the
same directory), and edit it with your Twitter app credentials. For further
information see [http://twitter4j.org/en/configuration.html]().

If running the app behind a proxy or filewall, copy the
`proxy.properties-template` file to a file named `proxy.properties` and set the
properties inside to your proxy credentials. If you feel uncomfortable putting
your proxy password in the file, leave the password-related ones commented and
the app will ask for the password.


## Usage
If you've just downloaded the binary distribution, do this from within the
unzipped archive (i.e. in the `fetch-tweets` directory). Otherwise, if you've
just built the app from source, do this from within
`PROJECT_ROOT/build/install/fetch-tweets`:

<pre>
Usage: bin/fetch-tweets[.bat] [options]
  Options:
    -c, --credentials
      Properties file with Twitter OAuth credentials
      Default: ./twitter.properties
    -h, -?, --help
      Help
      Default: false
    -i, --id, --ids
      ID of tweet(s) to fetch
      Default: []
    -f, --ids-file
      File of tweet IDs to fetch (one per line)
    -k, --keep-file
      File of properties to keep (comma separated or one per line)
    -v, --debug, --verbose
      Debug mode
      Default: false
</pre>

Run the app referring to your file of seed tweets:
<pre>
prompt> bin/refetch-tweets \
    --ids-file data/test/test-ids-300.json \
    --ids 919984305559961600
</pre>

Run in GUI mode:
<pre>
prompt> bin/refetch-tweets
</pre>

In commandline mode (providing IDs on the commandline or in a file), this will
cause the JSON for those tweets (which are still valid - they may have been
deleted) to be written to `stdout`. If no IDs are provided on the commandline or
in a file, then a GUI is launched. Type in or copy and paste a tweet ID or a tweet
URL into the ID field and press the "Fetch" button to retrieve the JSON for
that tweet.

If the system property `initial.tweet.id` is set (with the `-D` flag), the value 
is used for the content for the tweet ID/URL text box in the GUI. For it to work,
it must be a valid tweet ID or URL (e.g. 
"https://twitter.com/ABCaustralia/status/927673379238313984");

## Rate limits

Attempts have been made to account for Twitter's rate limits, so at times the
app will pause, waiting until the rate limit has refreshed. It reports how long
it will wait when it does have to pause.
