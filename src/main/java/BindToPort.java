import org.eclipse.jetty.server.Server;

import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

public class BindToPort extends HttpServlet {
    public static void bindToPort() throws Exception {
        int port = 8080;
        if (System.getenv("PORT") != null)
            port = Integer.valueOf(System.getenv("PORT"));

        Server server = new Server(port);
        server.start();
        server.join();
    }

    public static void keepAwake() {
        try {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        URL url = new URL("https://bubbiesbot-heroku.herokuapp.com/");
                        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
                        httpURLConnection.connect();
                        int responseCode = httpURLConnection.getResponseCode();
                        System.out.println("Response code is " + responseCode + "!");
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }, 0, 300000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}