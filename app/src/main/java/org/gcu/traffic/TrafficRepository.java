package org.gcu.traffic;

import static org.gcu.traffic.TrafficData.*;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class TrafficRepository {
    private static final String BASE = "https://datex2.trafficscotland.org/rest/2.3/publications/";
    private static final int LIMIT = 12 * 1024 * 1024;
    private final Context context;
    private final DatexParser parser = new DatexParser();

    TrafficRepository(Context context) { this.context = context; }
    enum Source { LIVE, CACHE, SAMPLE, NONE }

    static final class Credentials {
        final String id, key;
        Credentials(String id, String key) { this.id = id; this.key = key; }
    }

    static final class Part {
        final Feed feed;
        final Source source;
        final DatexParser.Publication publication;
        final String note;
        Part(Feed feed, Source source, DatexParser.Publication publication, String note) {
            this.feed = feed; this.source = source; this.publication = publication; this.note = note;
        }
        String summary() {
            return feed.label + ": " + (source == Source.LIVE ? "Live" : source == Source.CACHE
                    ? "Cached" : source == Source.SAMPLE ? "Sample" : "Unavailable") +
                    (publication.time.isEmpty() ? "" : " · " + date(publication.time)) +
                    (note.isEmpty() ? "" : " (" + note + ")");
        }
    }

    static final class Snapshot {
        final List<Situation> all;
        final String status;
        Snapshot(Part incidents, Part works) {
            List<Situation> joined = new ArrayList<>(incidents.publication.situations);
            joined.addAll(works.publication.situations);
            all = immutable(joined);
            status = incidents.summary() + "\n" + works.summary();
        }
    }

    Snapshot load(Credentials credentials, boolean tryNetwork) {
        return new Snapshot(load(Feed.INCIDENT, credentials, tryNetwork), load(Feed.WORKS, credentials, tryNetwork));
    }

    private Part load(Feed feed, Credentials credentials, boolean tryNetwork) {
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), feed.name() + ".xml"));
        String error = "";
        if (tryNetwork && credentials != null) {
            try {
                String xml = fetch(feed, credentials);
                DatexParser.Publication publication = parser.parse(xml, feed);
                try {
                    FileOutputStream out = file.startWrite();
                    try { out.write(xml.getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
                    catch (IOException e) { file.failWrite(out); throw e; }
                } catch (IOException e) {
                    return new Part(feed, Source.LIVE, publication, "could not save cache");
                }
                return new Part(feed, Source.LIVE, publication, "");
            } catch (HttpStatus e) {
                error = e.code == 401 || e.code == 403 || (e.code >= 300 && e.code < 400)
                        ? "check API access and credentials"
                        : "HTTP " + e.code;
            } catch (IOException e) {
                error = "live request failed";
            } catch (org.xmlpull.v1.XmlPullParserException e) {
                error = "invalid live XML";
            }
        } else if (tryNetwork) error = "add credentials for live data";

        try (InputStream input = file.openRead()) {
            return new Part(feed, Source.CACHE, parser.parse(read(input), feed), error);
        } catch (IOException | org.xmlpull.v1.XmlPullParserException ignored) {
            try (InputStream input = context.getAssets().open(feed.asset)) {
                return new Part(feed, Source.SAMPLE, parser.parse(read(input), feed), error);
            } catch (IOException | org.xmlpull.v1.XmlPullParserException e) {
                return new Part(feed, Source.NONE, new DatexParser.Publication("", new ArrayList<>()), "no readable data");
            }
        }
    }

    private String fetch(Feed feed, Credentials credentials) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + feed.endpoint + "/Content.xml").openConnection();
        try {
            c.setConnectTimeout(9000); c.setReadTimeout(10000);
            // Never forward credentials through any redirect, and never log either value.
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/xml, text/xml, */*");
            String encoded = Base64.encodeToString((credentials.id + ":" + credentials.key)
                    .getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", encoded); // Exact format supplied in the coursework starter.
            int code = c.getResponseCode();
            if (code != 200) throw new HttpStatus(code);
            try (InputStream in = c.getInputStream()) { return read(in); }
        } finally { c.disconnect(); }
    }

    private static final class HttpStatus extends IOException {
        final int code;
        HttpStatus(int code) { this.code = code; }
    }

    static String read(InputStream input) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n;
        byte[] bytes = new byte[8192];
        while ((n = input.read(bytes)) != -1) {
            if (out.size() + n > LIMIT) throw new IOException("Response too large");
            out.write(bytes, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    void saveCredentials(Credentials credentials) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey());
        byte[] encrypted = cipher.doFinal((credentials.id + "\n" + credentials.key).getBytes(StandardCharsets.UTF_8));
        boolean stored = context.getSharedPreferences("traffic_access", Context.MODE_PRIVATE).edit()
                .putString("encrypted", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit();
        if (!stored) throw new IOException("Credential store write failed");
    }

    Credentials savedCredentials() throws Exception {
        android.content.SharedPreferences prefs = context.getSharedPreferences("traffic_access", Context.MODE_PRIVATE);
        String encrypted = prefs.getString("encrypted", "");
        if (encrypted.isEmpty()) return null;
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
                new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        String value = new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        String[] fields = value.split("\n", 2);
        return new Credentials(fields[0], fields[1]);
    }

    void forgetCredentials() {
        context.getSharedPreferences("traffic_access", Context.MODE_PRIVATE).edit().clear().apply();
    }

    private SecretKey encryptionKey() throws Exception {
        String alias = "traffic.scotland.credentials";
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            return generator.generateKey();
        }
        return (SecretKey) store.getKey(alias, null);
    }
}
