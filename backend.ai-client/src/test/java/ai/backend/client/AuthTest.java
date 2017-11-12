package ai.backend.client;

import org.junit.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

public class AuthTest {
    @Test
    public void getCredentialString() {
        Config config = new Config.Builder().accessKey("TESTESTSERSERESTSET").secretKey("KJSAKDFJASKFDJASDFJSAFDJSJFSAJFSDF").build();

        Auth auth = new Auth(config);
        String body = "";

        SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        ISO8601DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));
        String date_s = "2017-10-28T19:57:56";
        Date date = new Date();
        try {
            date = ISO8601DATEFORMAT.parse(date_s);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        String a = auth.getCredentialString("POST", "/v2/kernel/create", date, body);
        assertEquals(a, "TESTESTSERSERESTSET:bdd29d9fa19f7dbe51883db01f9ed51eda7075230df44bf20b2e0e19a03682b4");

    }
}