package org.coms4200.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class PutRequest {
    private HttpURLConnection connection;
    private String address;
    private String content;

    private final Logger log = LoggerFactory.getLogger(getClass());

    public PutRequest(String address, String content) {
        this.address = address;
        this.content = content;
        try {
            URL url = new URL(address);
            connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", "" + content.getBytes().length);
            connection.setRequestProperty("Content-Language", "en-US");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
        } catch (Exception e) {
            log.info("Failed to create request for " + address + ". " + e.getMessage());
        }
    }

    public String execute() {
        try {
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(content);
            wr.close();

            InputStream is = connection.getInputStream();
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
                response.append('\r');
            }
            rd.close();
            return response.toString();
        } catch (Exception e) {
            log.info("Failed to execute request for " + address + " and content \n" + content + "\n" + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return "";
    }
}
