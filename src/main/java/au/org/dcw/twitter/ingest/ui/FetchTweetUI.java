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
import javax.swing.JLabel;
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
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class FetchTweetUI extends JPanel {

    private final ObjectMapper json;
    private static List<String> fieldsToKeep = Arrays.asList(
        "created_at", "text", "user.screen_name", "coordinates", "place", "entities.media"
    );
    private final Map<String, Object> fieldsToKeepMap;

    public FetchTweetUI(Twitter twitter) {
        buildUI(twitter);
        json = new ObjectMapper();
        fieldsToKeepMap = buildFieldStrippingStructure(fieldsToKeep);

//        System.out.println(str(fieldsToKeepMap, 0));
    }

    private String str(Map<String, Object> m, int indent) {
        StringBuilder sb = new StringBuilder("{\n");
        m.forEach((k, v) -> {
            sb.append(leadingSpace(indent + 1)).append(k).append(": ");
            if (v instanceof Map) {
                Map m2 = (Map) v;
                if (m2.isEmpty()) {
                    sb.append("" + v + "\n"); // null-safe
                } else {
                    sb.append(str((Map) v, indent + 1));
                }
            }
        });
        return sb.append(leadingSpace(indent) + "}\n").toString();
    }

    private String leadingSpace(int tabs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabs; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private Map<String, Object> buildFieldStrippingStructure(final List<String> fieldsToKeep) {
        Map<String, Object> map = Maps.newTreeMap();

        for (String f : fieldsToKeep) {
            if (! f.contains(".")) {
                map.put(f, Collections.emptyMap());
            } else {
                String topKey = f.substring(0, f.indexOf('.'));
                final String theRest = f.substring(f.indexOf('.') + 1);
                Map<String, Object> subMap = buildFieldStrippingStructure(Arrays.asList(theRest));
                if (map.containsKey(topKey)) {
                    final Map<String, Object> existingMap = (Map<String, Object>) map.get(topKey);
                    subMap.forEach((k, v) -> existingMap.put(k, v));
                } else {
                    map.put(topKey, subMap);
                }
            }

        }

        return map;
    }

    private void buildUI(final Twitter twitter) {

        // structure
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

        final JTextField tweetIdText = new JTextField();
        tweetIdText.setToolTipText("Paste or drag your tweet/status ID or URL here");
        tweetIdLabel.setLabelFor(tweetIdText);

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
        fetchButton.setToolTipText("Fetch the JSON for this tweet");
        fetchButton.requestFocus();

        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = row - 1;
        gbc.insets = new Insets(0, 2, 0, 0);
        this.add(fetchButton, gbc);


        // Row 2: titled panel with scrollable JSON text area and copy button
        row = 2;
        final JPanel fullJsonPanel = makeTitledPanel(" Full JSON ");

        // row 2.1: scrollable JSON text area
        final JTextArea fullJsonTextArea = new JTextArea();
        final Font jsonFont = new Font("Courier", Font.PLAIN, 12);
        fullJsonTextArea.setFont(jsonFont);
        fullJsonTextArea.setEditable(false);
        fullJsonTextArea.setLineWrap(true);
        fullJsonTextArea.setWrapStyleWord(true);
        fullJsonTextArea.setToolTipText("Click to copy");

        final JScrollPane fullJsonScrollPane = new JScrollPane(fullJsonTextArea);
        fullJsonScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        fullJsonScrollPane.setPreferredSize(new Dimension(250, 250));

        gbc = new GridBagConstraints();
        gbc.weighty = 1.0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        fullJsonPanel.add(fullJsonScrollPane, gbc);

        // Row 2.2: Copy button for full JSON
        final JButton copyFullJSONButton = new JButton("Copy");
        copyFullJSONButton.setToolTipText("Copy full JSON");

        gbc = new GridBagConstraints();
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        fullJsonPanel.add(copyFullJSONButton, gbc);


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


        // Row 3: titled panel with scrollable JSON text area and copy button
        row = 3;
        final JPanel strippedJsonPanel = makeTitledPanel(" Stripped JSON ");
        strippedJsonPanel.setToolTipText(makeStrippedTAExplanatoryTooltipText());

        // row 3.1: scrollable JSON text area
        final JTextArea strippedJsonTextArea = new JTextArea();
        strippedJsonTextArea.setFont(jsonFont);
        strippedJsonTextArea.setEditable(false);
        strippedJsonTextArea.setLineWrap(true);
        strippedJsonTextArea.setWrapStyleWord(true);
        strippedJsonTextArea.setToolTipText("Click to copy");

        final JScrollPane strippedJsonScrollPane = new JScrollPane(strippedJsonTextArea);
        strippedJsonScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        strippedJsonScrollPane.setPreferredSize(new Dimension(250, 250));

        gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        strippedJsonPanel.add(strippedJsonScrollPane, gbc);

        // Row 3.2: Copy button for stripped JSON
        final JButton copyStrippedJSONButton = new JButton("Copy");
        copyStrippedJSONButton.setToolTipText("Copy stripped JSON");

        gbc = new GridBagConstraints();
        gbc.gridy = 1;
        gbc.anchor = GridBagConstraints.WEST;
        strippedJsonPanel.add(copyStrippedJSONButton, gbc);

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


        // Row 4: quit button
        row = 4;
        final JButton quitButton = new JButton("Quit");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = row - 1;
        gbc.anchor = GridBagConstraints.EAST;
//        this.add(quitButton, gbc);


        // click text area -> selects all text
        fullJsonTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                fullJsonTextArea.setSelectionStart(0);
                final String text = fullJsonTextArea.getText();
                fullJsonTextArea.setSelectionEnd(text.length());
                pushToClipboard(text);
            }
        });
        strippedJsonTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                strippedJsonTextArea.setSelectionStart(0);
                strippedJsonTextArea.setSelectionEnd(strippedJsonTextArea.getText().length());
                pushToClipboard(strippedJsonTextArea.getText());
            }
        });
        // quit button
        quitButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.exit(0);
            }
        });
        // copy full JSON button
        copyFullJSONButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final String selectedText = fullJsonTextArea.getSelectedText();
                pushToClipboard(selectedText == null ? fullJsonTextArea.getText() : selectedText);
            }
        });
        // copy stripped JSON button
        copyStrippedJSONButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final String selectedText = strippedJsonTextArea.getSelectedText();
                pushToClipboard(selectedText == null ? strippedJsonTextArea.getText() : selectedText);
            }
        });
        // clear the text field
        clearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                tweetIdText.setText("");
            }
        });
        // fetch button
        fetchButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final String txt = tweetIdText.getText();
                // Be nice, do this on a background thread
                new Thread(() -> {
                    try {
                        // allow the user to paste in a full status URL or just the ID
                        // e.g. "https://twitter.com/KathViner/status/919984305559961600" or "919984305559961600"
                        final String idStr = txt.contains("/") ? txt.substring(txt.lastIndexOf('/') + 1) : txt;

                        System.out.println("Retrieving tweet: " + idStr);

                        final long tweetID = Long.parseLong(idStr);
                        Status tweet = twitter.showStatus(tweetID);
                        String rawJSON = TwitterObjectFactory.getRawJSON(tweet);
                        String strippedJSON = stripJSON(rawJSON);
                        SwingUtilities.invokeLater(() -> {
                            fullJsonTextArea.setText(rawJSON);
                            fullJsonTextArea.setCaretPosition(0); // scroll back to top
                            strippedJsonTextArea.setText(strippedJSON);
                            strippedJsonTextArea.setCaretPosition(0); // scroll back to top
                        });
                    } catch (NumberFormatException nfe) {
                        String errMsg = "ERROR: \"" + txt + "\" is not a valid tweet ID or URL.";
                        SwingUtilities.invokeLater(() -> fullJsonTextArea.setText(errMsg));
                    } catch (TwitterException twerr) {
                        String errMsg = "ERROR: Failed to retrieve tweet:\n" + twerr.getErrorMessage();
                        SwingUtilities.invokeLater(() -> fullJsonTextArea.setText(errMsg));
                        twerr.printStackTrace();
                    }
                }).start();
            }
        });
    }

    private String makeStrippedTAExplanatoryTooltipText() {
        StringBuilder sb = new StringBuilder("<html>");
        sb.append("JSON stripped of all fields except:<br>");
        for (String f : fieldsToKeep) {
            sb.append("- <code>").append(f).append("</code><br>");
        }
        return sb.append("</html>").toString();
    }

    private JPanel makeTitledPanel(final String label) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(label),
            BorderFactory.createEmptyBorder(5,5,5,5))
        );
        return panel;
    }

    private String stripJSON(final String tweetJSON) {
        try {
            JsonNode root = json.readValue(tweetJSON, JsonNode.class);

            stripFields(root, fieldsToKeepMap);

            return json.writeValueAsString(root);

        } catch (IOException e) {
            e.printStackTrace();
            StringWriter sw = new StringWriter();
            PrintWriter stacktrace = new PrintWriter(sw);
            e.printStackTrace(stacktrace);
            return "{\"error\":\"" + e.getMessage() + "\",\"stacktrace\":\"" + sw.toString() + "\"}";
        }
    }

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
            if (! value.isEmpty()) {
                stripFields(root.get(field), value);
            }
        }
    }

    private void pushToClipboard(String text) {
        final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }
}