import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.oauth.DbxCredential;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BubbiesBot extends ListenerAdapter {
    public static void main(String[] args) throws Exception {
        JDA tythorJDA = JDABuilder.createDefault(System.getenv("tythor")).build().awaitReady();
        JDA ashlingJDA = JDABuilder.createDefault(System.getenv("ashling")).build().awaitReady();

        System.out.println("Finished Building JDA!");

        Map<String, List<Message>> messageHistory = new HashMap<>();
        for (TextChannel textChannel : tythorJDA.getTextChannels()) {
            if (textChannel.getGuild().getName().equals("Bubbies"))
                messageHistory.put(textChannel.getName(), textChannel.getHistory().retrievePast(100).complete());
        }

        ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

        // Create Dropbox client
        DbxRequestConfig config = new DbxRequestConfig("");
        DbxCredential dbxCredential = new DbxCredential("", 0L, System.getenv("refreshToken"), System.getenv("appKey"), System.getenv("appSecret"));
        DbxClientV2 client = new DbxClientV2(config, dbxCredential.refresh(config).getAccessToken());
        DbxDownloader<FileMetadata> downloader = client.files().download("/bubbies.txt");

        InputStream inputStream = downloader.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        while (reader.ready()) {
            String line = reader.readLine();
            String[] contents = line.split("\\|");
            String author = contents[1];

            Task task = null;
            if (author.equals("TripleFury")) {
                task = new Task(tythorJDA, messageHistory, line);
            } else if (author.equals("ashling")) {
                task = new Task(ashlingJDA, messageHistory, line);
            }

            scheduledExecutorService.schedule(task, task.delay, task.timeUnit);
        }

        System.out.println("Finished scheduling tasks!");
    }

    static class Task implements Runnable {
        public long delay;
        public TimeUnit timeUnit;

        private String message;
        private TextChannel textChannel;
        private boolean sendMessage;

        public Task(JDA jda, Map<String, List<Message>> messageHistory, String line) {
            String[] contents = line.split("\\|");
            String channel = contents[0];
            String author = contents[1];
            String time = contents[2];
            message = contents.length == 4 ? contents[3].replace("\\\\n", "\n") : "`attachment`";

            textChannel = jda.getTextChannelsByName(channel, false).get(0);

            LocalDateTime sendTime = LocalDateTime.parse(time).plusYears(2);
            delay = ChronoUnit.MICROS.between(LocalDateTime.now(), sendTime);
            timeUnit = TimeUnit.MICROSECONDS;

            // If Heroku goes down for < 5 minutes, the messages are still sent
            if (delay >= 0) {
                sendMessage = true;
            } else if (delay > -timeUnit.convert(5, TimeUnit.MINUTES)) {
                sendMessage = true;

                // If message already exists in last 100 messages in channel, don't send it
                List<Message> messageList = messageHistory.get(channel);
                for (Message m : messageList) {
                    if (m.getContentRaw().equals(message)) {
                        String messageAuthor = m.getAuthor().getName();
                        if (author.equals("TripleFury") && messageAuthor.equals("TythorBot") || author.equals("ashling") && messageAuthor.equals("AshlingBot")) {
                            LocalDateTime timeCreated = m.getTimeCreated().toLocalDateTime();
                            if (timeCreated.isBefore(sendTime.plusMinutes(5))) {
                                sendMessage = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        public void run() {
            if (sendMessage) {
                textChannel.sendMessage(message).complete();
                System.out.printf("[%s] %s: %s\n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM d HH:mm:ss:SSS")), textChannel.getJDA().getSelfUser().getName(), message);
            }
        }
    }
}
