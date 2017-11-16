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
package au.org.dcw.twitter.ingest.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * <p>Creates a GUI for fetching a single Tweet at a time, and displaying the JSON for
 * the Tweet, and a valid JSON subset of the Tweet, either of which can be copied by
 * clicking in their text areas or using the copy buttons. Fields which are retained
 * in the subset are declared in {@link #fieldsToKeep} and {@link #FIELDS_TO_KEEP2}
 * (the latter ignores the Tweet's <code>entities.media</code> field).</p>
 *
 * TODO Make {@link #fieldsToKeep} configurable.
 */
@SuppressWarnings("unchecked")
public class FetchTweetUI extends JPanel {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> fieldsToKeep;
//    = Arrays.asList(
//        "created_at", "text", "full_text", "extended_tweet.full_text", "user.screen_name", "coordinates", "place",
//        "entities.media", "id", "id_str"
//    );
    // skips entities.media, in case images are sensitive
    private final List<String> fieldsToKeepNoMedia;
//    = Arrays.asList(
//        "created_at", "text", "full_text", "extended_tweet.full_text", "user.screen_name", "coordinates", "place",
//        "id", "id_str"
//    );

    private static final Font JSON_FONT = new Font("Courier New", Font.PLAIN, 10);
    private static final String INDENT = "  ";

    private final boolean debug;

    private JTextField tweetIdText;
    private JTextArea fullJsonTextArea;
    private JTextArea strippedJsonTextArea;
    private JCheckBox skipMediaCheckbox;
    private boolean errorState;


    /**
     * Constructor
     *
     * @param twitter The reference to Twitter's API, provided by {@link au.org.dcw.twitter.ingest.FetchTweets}.
     * @param debug If true, print out debug statements.
     */
    public FetchTweetUI(
        final Twitter twitter,
        final List<String> fieldsToKeep,
        final boolean debug
    ) {
        this.fieldsToKeep = fieldsToKeep;
        this.fieldsToKeepNoMedia = Lists.newArrayList(fieldsToKeep);
        if (! fieldsToKeepNoMedia.remove("entities.media")) { // media-safe list
            System.err.println("\"entities.media\" wasn't required - skip images checkbox will have no effect.");
        }
        this.debug = debug;
        if (debug) System.out.println(str(buildFieldStructure(this.fieldsToKeep), 0));
        buildUI(twitter);
    }

    /**
     * Constructs a nested map of the fields to retain in the stripped version
     * of the the Tweet's JSON.
     *
     * @param fieldsToKeep The list of field names with implied structure (via '.' delimiters).
     * @return A nested map version of <code>fieldsToKeep</code>.
     */
    private static Map<String, Object> buildFieldStructure(final List<String> fieldsToKeep) {
        Map<String, Object> map = Maps.newTreeMap();

        for (String f : fieldsToKeep) {
            if (! f.contains(".")) {
                map.put(f, null);
            } else {
                final String head = f.substring(0, f.indexOf('.'));
                final String tail = f.substring(f.indexOf('.') + 1);
                final Map<String, Object> subMap = buildFieldStructure(Collections.singletonList(tail));
                if (map.containsKey(head)) {
                    final Map<String, Object> existingMap = (Map<String, Object>) map.get(head);
                    existingMap.putAll(subMap);
                } else {
                    map.put(head, subMap);
                }
            }
        }
        return map;
    }

    /**
     * Builds the UI.
     *
     * @param twitter The Twitter API instance, used by an event handler.
     */
    private void buildUI(final Twitter twitter) {

        // STRUCTURE
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5,5,5,5));


        // Row 1: ID text box and Get button
        int row = 1;
        final JLabel tweetIdLabel = new JLabel();
        tweetIdLabel.setText("Tweet ID/URL:");
        tweetIdLabel.setToolTipText("Paste or drag your tweet/status ID or URL here");
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(0, 0, 0, 5);
        this.add(tweetIdLabel, gbc);

        tweetIdText = new JTextField();
        tweetIdText.setToolTipText("Paste or drag your tweet/status ID or URL here");
        tweetIdLabel.setLabelFor(tweetIdText);
        tweetIdText.setDragEnabled(true);
        // dragging text to the field _replaces_ the text, rather than inserting it where it's dropped
        tweetIdText.setTransferHandler(new DropToReplaceTransferHandler());

        // example tweet
        tweetIdText.setText("https://twitter.com/ABCaustralia/status/927673379238313984");

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = row - 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        this.add(tweetIdText, gbc);

        final JButton clearButton = new JButton(UIManager.getIcon("InternalFrame.closeIcon"));
        clearButton.setToolTipText("Clear the ID field");

        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        gbc.gridy = row - 1;
        gbc.insets = new Insets(0, 5, 0, 0);
        this.add(clearButton, gbc);

        final JButton fetchButton = new JButton("Fetch");
        fetchButton.setToolTipText("Fetch the JSON for this tweet, put it below and in the copy buffer");
        fetchButton.requestFocus();

        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = row - 1;
        gbc.insets = new Insets(0, 2, 0, 0);
        this.add(fetchButton, gbc);


        // Row 2: titled panel with scrollable JSON text area and copy button
        row = 2;
        fullJsonTextArea = new JTextArea(); // Row 2.1
        final JPanel fullJsonPanel = makeJsonPanel(" Full JSON (Click to copy text) ", fullJsonTextArea);

        // Row 2.2: sneak in a paste-from-clipboard button within the group panel
        /* NB I can't rely on selecting within the text area and hitting Ctrl-V, because
         * when you click within the text area, you copy the text area to the global copy
         * buffer, thus getting rid of what you're trying to paste in. Sigh.
         */
        final JButton pasteFromClipboardButton = new JButton("Paste from clipboard");

        gbc = new GridBagConstraints();
        gbc.gridy = 1; // fullJsonTextArea is gridy == 0
        gbc.fill = GridBagConstraints.HORIZONTAL;
        fullJsonPanel.add(pasteFromClipboardButton, gbc);

        // add full JSON titled panel to outer
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row - 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(5, 0, 5, 0);
        this.add(fullJsonPanel, gbc);


        // Row 3: skip-media checkbox
        row = 3;
        skipMediaCheckbox = new JCheckBox("Skip images & videos?");
        skipMediaCheckbox.setSelected(false);
        skipMediaCheckbox.setToolTipText("Select this to remove the Tweet's media field");

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row - 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridwidth = 4;
        this.add(skipMediaCheckbox, gbc);


        // Row 4: titled panel with scrollable JSON text area and copy button
        row = 4;
        strippedJsonTextArea = new JTextArea();
        final JPanel strippedJsonPanel =
            makeJsonPanel(" Stripped JSON (Click to copy text) ", strippedJsonTextArea);
        strippedJsonPanel.setToolTipText(makeExplanatoryTooltip());

        // add stripped JSON titled panel to outer
        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row - 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(5, 0, 5, 0);
        this.add(strippedJsonPanel, gbc);


        // BEHAVIOUR
        // clear the text field
        clearButton.addActionListener(e -> tweetIdText.setText(""));
        // update the stripped JSON anytime the checkbox is changed
        skipMediaCheckbox.addActionListener(e -> updateStrippedJson(fullJsonTextArea.getText()));
        // paste from clipboard to the full json text area
        pasteFromClipboardButton.addActionListener(e -> {
            final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            try {
                // grab the text from the clipboard, safely
                final String hopefullyJSON = (String) clipboard.getData(DataFlavor.stringFlavor);
                if (hopefullyJSON != null) {
                    // update the text areas
                    updateTextArea(fullJsonTextArea, hopefullyJSON);
                    updateStrippedJson(hopefullyJSON);
                }
            } catch (UnsupportedFlavorException | IOException e1) {
                e1.printStackTrace();
                JOptionPane.showMessageDialog(
                    fullJsonTextArea,
                    "Failed to paste:\n" + e1.getMessage(),
                    "Paste Error",
                    JOptionPane.WARNING_MESSAGE
                );
            }
        });
        // fetch button - fetch tweet JSON and update text areas
        fetchButton.addActionListener(e -> {
            final String txt = tweetIdText.getText();
            // Be nice, do this on a background thread
            new Thread(() -> {
                try {
                    errorState = false;
                    // allow the user to paste in a full status URL or just the ID
                    // e.g. "https://twitter.com/KathViner/status/919984305559961600" or "919984305559961600"
                    final String idStr = txt.contains("/")
                        ? txt.substring(txt.lastIndexOf('/') + 1).trim()
                        : txt.trim();

                    System.out.println("Retrieving tweet: " + idStr);

                    final long tweetID = Long.parseLong(idStr);
                    final Status tweet = twitter.showStatus(tweetID);
                    final String rawJSON = TwitterObjectFactory.getRawJSON(tweet);

                    updateTextArea(fullJsonTextArea, rawJSON);
                    updateStrippedJson(rawJSON);
                    pushToClipboard(rawJSON);

                } catch (NumberFormatException nfe) {
                    String errMsg = "ERROR: \"" + txt + "\" is not a valid tweet ID or URL.";
                    SwingUtilities.invokeLater(() -> fullJsonTextArea.setText(errMsg));
                    errorState = true;
                } catch (TwitterException twerr) {
                    String errMsg = "ERROR: Failed to retrieve tweet:\n" + twerr.getErrorMessage();
                    SwingUtilities.invokeLater(() -> fullJsonTextArea.setText(errMsg));
                    twerr.printStackTrace();
                    errorState = true;
                }
            }).start();
        });
    }

    /**
     * Updates the stripped JSON area, taking into account whether images are wanted.
     * Does nothing when the input is invalid. May be called from the Swing thread or
     * off of it.
     *
     * @param rawJSON The raw JSON to consider.
     */
    private void updateStrippedJson(final String rawJSON) {
        if (errorState || rawJSON == null || rawJSON.length() == 0) return;

        final Map<String, Object> toKeep =
            buildFieldStructure(skipMediaCheckbox.isSelected() ? fieldsToKeepNoMedia : this.fieldsToKeep);

        final String strippedJSON = stripJSON(rawJSON, toKeep);

        updateTextArea(strippedJsonTextArea, strippedJSON);
    }

    /**
     * Updates a {@link JTextArea} safely, from on or off the UI thread.
     *
     * @param textArea The text area to update.
     * @param text The text to put into it.
     */
    private void updateTextArea(final JTextArea textArea, final String text) {
        SwingUtilities.invokeLater(() -> {
            textArea.setText(text);
            textArea.setCaretPosition(0); // scroll back to top
        });
    }

    /**
     * Creates a titled, bordered panel which includes a text area for the JSON.
     * Clicking on the text area puts the JSON into the global copy buffer.
     *
     * @param title The title of the panel.
     * @param jsonTextArea The text area to put in the panel.
     * @return The titled panel.
     */
    private JPanel makeJsonPanel(
        final String title,
        final JTextArea jsonTextArea
    ) {
        // STRUCTURE
        JPanel panel = makeTitledPanel(title);

        // configure the text area and make it scrollable
        jsonTextArea.setFont(JSON_FONT);
        jsonTextArea.setEditable(false);
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);
        jsonTextArea.setToolTipText("Click the text to copy it");

        final JScrollPane strippedJsonScrollPane = new JScrollPane(jsonTextArea);
        strippedJsonScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        strippedJsonScrollPane.setPreferredSize(new Dimension(250, 250));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        panel.add(strippedJsonScrollPane, gbc);

        // BEHAVIOUR
        // click text area -> selects all text and puts it on the copy clipboard
        final MouseAdapter copyAction = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                jsonTextArea.setSelectionStart(0);
                final String text = jsonTextArea.getText();
                jsonTextArea.setSelectionEnd(text.length());
                if (debug) System.out.println("Pushing '" + text + "' to the clipboard");
                pushToClipboard(text);
            }
        };
        jsonTextArea.addMouseListener(copyAction);
        panel.addMouseListener(copyAction);

        return panel;
    }

    /**
     * Creates a String to use as a tooltip to explain which fields are retained
     * in the stripped version of the JSON.
     *
     * @return An explanatory tooltip.
     */
    private String makeExplanatoryTooltip() {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("The JSON is filtered for only these fields:<br>");
        fieldsToKeep.stream()
            .sorted()
            .map(f -> "- <code>" + f + "</code><br>")
            .forEach(sb::append);
        sb.append("<strong>NB</strong> <code>entities.media</code> is not included if images are skipped.<br>");
        return sb.append("</html>").toString();
    }

    /**
     * Creates a {@link JPanel} with a titled border and a {@link GridBagLayout}.
     *
     * @param title The title to use for the panel's border.
     * @return A panel with a titled border.
     */
    private JPanel makeTitledPanel(final String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(title),
            BorderFactory.createEmptyBorder(0,5,5,5))
        );
        return panel;
    }

    /**
     * Strips sensitive elements from the Tweet's raw JSON.
     *
     * @param tweetJSON The Tweet's raw JSON.
     * @param fieldsToKeep Keep the fields in this nested map.
     * @return The desensitised JSON.
     */
    private String stripJSON(final String tweetJSON, Map<String, Object> fieldsToKeep) {
        try {
            JsonNode root = JSON.readValue(tweetJSON, JsonNode.class);

            stripFields(root, fieldsToKeep);

            /* As of 2017-09-27, Twitter is progressively rolling out 280 character tweets,
             * referred to as "extended tweets", and "text" is replaced by "full_text". I am
             * using Twitter4J in extended mode, but as a courtesy to those still running on
             * standard mode, my "stripped" objects will have "full_text" copied to "text", if
             * there is no content there already.
             */
            if (! root.has("text")) {
                ((ObjectNode) root).set("text", root.get("full_text").deepCopy());
            }

            return JSON.writeValueAsString(root);

        } catch (IOException e) {
            e.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter stacktrace = new PrintWriter(sw);
            e.printStackTrace(stacktrace);
            return "{\"error\":\"" + e.getMessage() + "\",\"stacktrace\":\"" + sw.toString() + "\"}";
        }
    }

    /**
     * Strips unwanted fields directly from a {@link JsonNode} tree structure.
     *
     * @param root The root of the tree.
     * @param toKeep The fields to keep - i.e. remove the others.
     */
    private void stripFields(final JsonNode root, final Map<String, Object> toKeep) {

        List<String> toRemove = Lists.newArrayList();

        final Iterator<String> fieldIterator = root.fieldNames();
        while (fieldIterator.hasNext()) {
            String field = fieldIterator.next();
            if (! toKeep.containsKey(field)) {
                toRemove.add(field);
            }
        }
        ((ObjectNode) root).remove(toRemove);

        for (String field: toKeep.keySet()) {
            Map<String, Object> value = (Map<String, Object>) toKeep.get(field);
            if (value != null && root.has(field)) {
                stripFields(root.get(field), value);
            }
        }
    }

    /**
     * Put the provided text into the global copy buffer.
     *
     * @param text The text to put into the copy buffer.
     */
    private void pushToClipboard(String text) {
        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    /**
     * Converts a nested map (keys only) to a formatted String. Ignores values
     * unless they're nested maps. Recursively called, making use of
     * <code>indentLevel</code> to structure the text.
     *
     * @param m The nested map to represent as a String.
     * @param indentLevel How deeply we've recursed.
     * @return A formatted String representation of a nested map.
     */
    private String str(final Map<String, Object> m, final int indentLevel) {
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> {
            sb.append(leadingSpaces(indentLevel)).append("- ").append(k).append("\n");

            Map mapValue = (Map) v;
            if (v != null) {
                sb.append(str(mapValue, indentLevel + 1));
            }
        });
        return sb.toString();
    }

    /**
     * Creates the leading spaces used in formatting, based on {@link #INDENT}.
     *
     * @param tabs The number of times we've indented.
     * @return A String of spaces.
     */
    private String leadingSpaces(final int tabs) {
        return IntStream.range(0, tabs).mapToObj(i -> INDENT).collect(Collectors.joining());
    }
}
