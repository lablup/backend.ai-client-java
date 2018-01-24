package ai.backend.client;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class Auth {

    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final String apiVersion;
    private final String hashType;
    private final String hostname;

    public Auth(ClientConfig config) {
        this.accessKey = config.getAccessKey();
        this.secretKey = config.getSecretKey();
        this.endpoint = config.getEndPoint();
        this.apiVersion = config.getApiVersion();
        this.hostname = config.getHostname();
        this.hashType = config.getHashType();
    }

    public String getCredentialString(String method, String queryString, Date date, String contentType, String bodyValue)
    {
        byte[] signKey = this.getSignKey(this.secretKey, date);
        byte[] authenticationBytes = this.sign(signKey, this.getAuthenticationString(method, queryString, date, contentType, bodyValue));
        String authenticationString = bytesToHex(authenticationBytes);

        return String.format("%s:%s", this.accessKey, authenticationString);
    }

    private String getAuthenticationString(String method, String queryString, Date date, String contentType, String bodyValue) {

        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        SimpleDateFormat ISO8601DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        ISO8601DATEFORMAT.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        String dstring = String.format("%s%s", ISO8601DATEFORMAT.format(date), "+00:00");
        String hstring = bytesToHex(digest.digest(bodyValue.getBytes()));

        String result = String.format("%s\n%s\n%s\nhost:%s\ncontent-type:%s\nx-backendai-version:%s\n%s", method, queryString, dstring, this.hostname, contentType, this.apiVersion, hstring);

        return result;
    }

    private byte[] getSignKey(String secretKey, Date date) {
        SimpleDateFormat sf = new SimpleDateFormat("yyyyMMdd");
        sf.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        byte[] k1 = this.sign(secretKey.getBytes(), sf.format(date));
        byte[] k2 = this.sign(k1, this.hostname);

        return k2;
    }


    private byte[] sign(byte[] key, String data){
        Mac mac = null;
        try {
            mac = Mac.getInstance(this.hashType);
            mac.init(new SecretKeySpec(key, this.hashType));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        }
        return mac.doFinal(data.getBytes());
    }


    private static String bytesToHex(byte[] bytes) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}