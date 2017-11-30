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
        assertEquals(a, "TESTESTSERSERESTSET:dcd926f4b281e05d384b3debccd540b1cd9ad30c184f5797057616f3b86b2cc3");

    }
}