/*
 * Copyright (C) 2014 cnaude
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.cnaude.purpleirc;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import org.pircbotx.Channel;
import org.pircbotx.User;

/**
 *
 * @author Chris Naude Poll the command queue and dispatch to Bukkit
 */
public class IRCMessageQueueWatcher {

    private final PurpleIRC plugin;
    private final PurpleBot ircBot;
    private final Timer timer;
    private final BlockingQueue<IRCMessage> queue = new LinkedBlockingQueue<>();
    private final String REGEX_CLEAN = "^[\\r\\n]|[\\r\\n]$";
    private final String REGEX_CRLF = "\\r\\n";
    private final String LF = "\\n";

    /**
     *
     * @param plugin the PurpleIRC plugin
     * @param ircBot
     */
    public IRCMessageQueueWatcher(final PurpleBot ircBot, final PurpleIRC plugin) {
        this.plugin = plugin;
        this.ircBot = ircBot;
        this.timer = new Timer();
        startWatcher();
    }

    private void startWatcher() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                queueAndSend();
            }

        }, 0, 5);
    }

    private void queueAndSend() {
        IRCMessage ircMessage = queue.poll();
        if (ircMessage != null) {
            plugin.logDebug("[" + queue.size() + "]: queueAndSend message detected");
            for (String s : cleanupAndSplitMessage(ircMessage.message)) {
                switch (ircMessage.type) {
                    case MESSAGE:
                        blockingIRCMessage(ircMessage.target, s);
                        break;
                    case CTCP:
                        blockingCTCPMessage(ircMessage.target, s);
                        break;
                    case NOTICE:
                        blockingNoticeMessage(ircMessage.target, s);
                }
            }
        }
    }

    private void blockingIRCMessage(final String target, final String message) {
        if (!ircBot.isConnected() || message.isEmpty()) {
            return;
        }
        plugin.logDebug("[blockingIRCMessage] About to send IRC message to " + target + ": " + message);
        ircBot.bot.sendIRC().message(target, message);
        plugin.logDebug("[blockingIRCMessage] Message sent to " + target + ": " + message);
    }

    private void blockingCTCPMessage(final String target, final String message) {
        if (!ircBot.isConnected() || message.isEmpty()) {
            return;
        }
        plugin.logDebug("[blockingCTCPMessage] About to send IRC message to " + target + ": " + message);
        ircBot.bot.sendIRC().ctcpResponse(target, message);
        plugin.logDebug("[blockingCTCPMessage] Message sent to " + target + ": " + message);
    }

    private void blockingNoticeMessage(final String target, final String message) {
        if (!ircBot.isConnected() || message.isEmpty()) {
            return;
        }
        plugin.logDebug("[blockingNoticeMessage] About to send IRC notice to " + target + ": " + message);
        ircBot.bot.sendIRC().notice(target, message);
        plugin.logDebug("[blockingNoticeMessage] Notice sent to " + target + ": " + message);
    }

    private String pingFix(String message) {
        try {
            for (Channel channel : ircBot.bot.getUserBot().getChannels()) {
                for (User user : channel.getUsers()) {
                    if (user.getNick().equalsIgnoreCase(ircBot.botNick)) {
                        continue;
                    }
                    if (message.toLowerCase().contains(user.getNick().toLowerCase())) {
                        message = message.replaceAll(
                                "(?i)" + user.getNick(),
                                Matcher.quoteReplacement(plugin.tokenizer.addZeroWidthSpace(user.getNick()))
                        );
                    }
                }
            }
        } catch (Exception ex) {
            plugin.logDebug(ex.getMessage());
        }
        return message;
    }

    private String[] cleanupAndSplitMessage(String message) {
        if (ircBot.pingFixFull) {
            message = pingFix(message);
        }
        return message.replaceAll(REGEX_CLEAN, "").replaceAll(REGEX_CRLF, "\n").split(LF);
    }

    public void cancel() {
        timer.cancel();
    }

    public String clearQueue() {
        int size = queue.size();
        if (!queue.isEmpty()) {
            queue.clear();
        }
        return "Elements removed from message queue: " + size;
    }

    /**
     *
     * @param ircMessage
     */
    public void add(IRCMessage ircMessage) {
        queue.offer(ircMessage);
    }

}
