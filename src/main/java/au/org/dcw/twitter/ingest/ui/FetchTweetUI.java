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

import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class FetchTweetUI extends JPanel {

    public FetchTweetUI(Twitter twitter) {
        buildUI(twitter);
    }

    private void buildUI(final Twitter twitter) {

        // structure
        setLayout(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(5,5,5,5));

        // top: ID text box and Get button
        final JLabel idLabel = new JLabel();
        idLabel.setText("Tweet ID: ");
        GridBagConstraints gbc = new GridBagConstraints();
        this.add(idLabel, gbc);

        final JTextField idText = new JTextField();
        idText.setToolTipText("Paste or drag your tweet/status ID or URL here");
        idLabel.setLabelFor(idText);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        this.add(idText, gbc);

        final JButton clearButton = new JButton(UIManager.getIcon("InternalFrame.closeIcon"));
        clearButton.setToolTipText("Clear the ID field");

        gbc = new GridBagConstraints();
        gbc.gridx = 2;
        this.add(clearButton, gbc);

        final JButton getButton = new JButton("Fetch");
        getButton.setToolTipText("Fetch the JSON for this tweet");
        getButton.requestFocus();

        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.insets = new Insets(0, 2, 0, 0);
        this.add(getButton, gbc);

        // middle: scrollable JSON text area
        final JTextArea jsonTextArea = new JTextArea();
        jsonTextArea.setFont(new Font("Courier", Font.PLAIN, 12));
        jsonTextArea.setEditable(false);
        jsonTextArea.setLineWrap(true);
        jsonTextArea.setWrapStyleWord(true);

        final JScrollPane jsonScrollPane = new JScrollPane(jsonTextArea);
        jsonScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        jsonScrollPane.setPreferredSize(new Dimension(250, 250));

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.gridwidth = 4;
        gbc.insets = new Insets(5, 0, 5, 0);
        this.add(jsonScrollPane, gbc);

        // bottom: copy and quit buttons
        final JButton copyButton = new JButton("Copy");

        gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 2;
        this.add(copyButton, gbc);

        final JButton quitButton = new JButton("Quit");
        gbc = new GridBagConstraints();
        gbc.gridx = 3;
        gbc.gridy = 2;
        gbc.anchor = GridBagConstraints.EAST;
        this.add(quitButton, gbc);

        // behaviour
        // click text area -> selects all text
        jsonTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                jsonTextArea.setSelectionStart(0);
                jsonTextArea.setSelectionEnd(jsonTextArea.getText().length());
            }
        });
        // clear the text field
        clearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                idText.setText("");
            }
        });
        // quit button
        quitButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                System.exit(0);
            }
        });
        // copy button
        copyButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final String selectedText = jsonTextArea.getSelectedText();
                final String jsonText = selectedText == null ? jsonTextArea.getText() : selectedText;
                // dump text into global copy buffer
                final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new StringSelection(jsonText), null);
            }
        });
        // get button
        getButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                final String txt = idText.getText();
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
                        SwingUtilities.invokeLater(() -> {
                            jsonTextArea.setText(rawJSON);
                            jsonTextArea.setCaretPosition(0); // scroll back to top
                        });
                    } catch (NumberFormatException nfe) {
                        String errMsg = "ERROR: \"" + txt + "\" is not a valid tweet ID or URL.";
                        SwingUtilities.invokeLater(() -> jsonTextArea.setText(errMsg));
                    } catch (TwitterException twerr) {
                        String errMsg = "ERROR: Failed to retrieve tweet:\n" + twerr.getErrorMessage();
                        SwingUtilities.invokeLater(() -> jsonTextArea.setText(errMsg));
                        twerr.printStackTrace();
                    }
                }).start();
            }
        });
    }
}
