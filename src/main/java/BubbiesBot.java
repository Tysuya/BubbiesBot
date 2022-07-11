import com.dropbox.core.DbxDownloader;
import com.dropbox.core.DbxRequestConfig;
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
        DbxRequestConfig config = new DbxRequestConfig("Tysuya");
        DbxClientV2 client = new DbxClientV2(config, System.getenv("dropbox"));
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

        BindToPort.keepAwake();
        //BindToPort.bindToPort();
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

            if (delay > -3600000000L && delay < 0) {
                sendMessage = true;

                List<Message> messageList = messageHistory.get(channel);
                // If message doesn't exist in channel, send it
                for (Message m : messageList) {
                    if (m.getContentRaw().equals(message)) {
                        if (author.equals("TripleFury") && m.getAuthor().getName().equals("TythorBot") || author.equals("ashling") && m.getAuthor().getName().equals("AshlingBot")) {
                            sendMessage = false;
                            break;
                        }
                    }
                }
            }
        }

        public void run() {
            if (sendMessage) {
                textChannel.sendMessage(message).complete();
            }
        }
    }
}
