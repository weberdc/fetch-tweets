# fetch-tweets

Author: **Derek Weber**

Last updated: **2017-11-03**

A tool to fetch tweets, with commandline and GUI modes.


## Description

This app will look for tweet IDs on the commandline, or from a file specified on
the commandline, or will launch a GUI into which can be pasted individual tweet IDs
or URLs, which can then be fetched. In command line mode, the JSON for the retrieved
tweets is sent to `stdout`.

Twitter credentials are looked for in `"./twitter.properties"`, and proxy info
is looked for in `"./proxy.properties"`. Commandline options for the input file,
the output file, and the Twitter properties are provided, along with a verbose
mode.


## Requirements:

 + Java Development Kit 1.8
 + [Twitter4J](http://twitter4j.org) (Apache 2.0 licence)
   + depends on [JSON](http://json.org) ([JSON licence](http://www.json.org/license.html))
 + [Google Guava](https://github.com/google/guava) (Apache 2.0 licence) + [FasterXML](http://wiki.fasterxml.com/JacksonHome) (Apache 2.0 licence)
 + [jcommander](http://jcommander.org) (Apache 2.0 licence)

Built with [Gradle 4.3.1](http://gradle.org), included via the wrapper.


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

## Rate limits

Attempts have been made to account for Twitter's rate limits, so at times the
app will pause, waiting until the rate limit has refreshed. It reports how long
it will wait when it does have to pause.
