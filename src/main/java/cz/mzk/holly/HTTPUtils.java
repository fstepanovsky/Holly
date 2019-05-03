package cz.mzk.holly;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author kremlacek
 */
public class HTTPUtils {

    private static int getResponseCodeDirect(String urlStr, String method) throws IOException {
        URL url = new URL(urlStr);

        System.out.println(urlStr);

        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        connection.setRequestMethod(method);
        connection.connect();

        int i = connection.getResponseCode();

        connection.disconnect();

        return i;
    }

    public static int getResponseCodeDirect(String urlStr) throws IOException {
        return getResponseCodeDirect(urlStr, "GET");
    }
}
