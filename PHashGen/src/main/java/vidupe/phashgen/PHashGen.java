package vidupe.phashgen;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.util.Map;

@WebServlet("/phashgen")
public class PHashGen {
    public void doGet(HttpServletRequest request, HttpServletResponse response) {

    }
    public StringBuilder receiveMessages() throws InterruptedException {
        TopicName topic = TopicName.of("winter-pivot-192220", "frontend-topic");
        SubscriptionName subscription = SubscriptionName.of("winter-pivot-192220", "filter-subscription");
        StringBuilder display = new StringBuilder("default");
        MessageReceiver receiver =
                new MessageReceiver() {
                    @Override
                    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
                        Map<String, String> attributesMap = message.getAttributesMap();
                        //attributesReceived.putAll(attributesMap);
                        generatePhash(attributesMap);
                        consumer.ack();
                    }
                };
        Subscriber subscriber = Subscriber.newBuilder(subscription, receiver).build();
        subscriber.addListener(
                new Subscriber.Listener() {
                    @Override
                    public void failed(Subscriber.State from, Throwable failure) {
                        // Handle failure. This is called when the Subscriber encountered a fatal error and is shutting down.
                        System.err.println(failure);
                    }
                },
                MoreExecutors.directExecutor());
        subscriber.startAsync().awaitRunning();

        return display;

    }

    private void generatePhash(Map<String, String> attributesMap) {
        String id = attributesMap.get("video_id");
        String accessToken = attributesMap.get("access_token");
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
        Drive drive = new Drive.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
                .setApplicationName("Duplicate video Detection").build();

        downloadVideo(attributesMap,drive);

    }

    private void downloadVideo(Map<String, String> attributesMap, Drive drive) {
        try {
           FileList result = drive.files().list().setFields(
                    "files(capabilities/canDownload,id,md5Checksum,mimeType,name,size,videoMediaMetadata,webContentLink)")
                    .execute();
           java.io.File dir = new java.io.File("gmail");
           dir.mkdir();
           URL url = new URL("https://www.googleapis.com/drive/v3/files/" + attributesMap.get("video_id") + "?alt=media");
           HttpRequest httpRequestGet = drive.getRequestFactory().buildGetRequest(new GenericUrl(url.toString()));
            httpRequestGet.getHeaders().setRange("bytes=" + 0 + "-");
           System.out.println(httpRequestGet.getHeaders());
           HttpResponse resp = httpRequestGet.execute();
           String pathname = "gmail" ;
           OutputStream outputStream = new FileOutputStream(
                    new java.io.File(pathname+"/"+ attributesMap.get("video_name")));
					resp.download(outputStream);
//           String command = "ffmpeg -y -i "+pathname+" -r 10 -s "+attributesMap.get("min_height")+"x"+attributesMap.get("min_width")+" -c:v libx264 -b:v 3M -strict -2 -movflags faststart "+pathname+"/destination1.mp4";
//           Process proc = Runtime.getRuntime().exec(command);
           extractKeyFrames(pathname+"/"+attributesMap.get("video_name"));
		   StringBuilder videoHashes = generateHashes(pathname);
		   writeInDataStore(videoHashes,attributesMap);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeInDataStore(StringBuilder videoHashes, Map<String, String> attributesMap) {
    }

    private StringBuilder generateHashes(String pathname) {

            ImagePhash imgphash = new ImagePhash();
            File directory = new File(pathname);
            StringBuilder video_hashes = new StringBuilder();
            File[] f = directory.listFiles();
            for (File file : f) {
            if (file != null && file.getName().toLowerCase().endsWith(".jpg")) {
                try {
                    String image1hash = imgphash.getHash(new FileInputStream(file));
                    video_hashes.append(image1hash+ "$");
//
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
       return video_hashes;
    }




    private void extractKeyFrames(String pathname) {
        String command="ffmpeg -i "+pathname+" -vf select=eq(pict_type\\,PICT_TYPE_I) -vsync vfr "+ pathname +"/thumb2_%04d.jpg -hide_banner -y";
        Runtime runtime = Runtime.getRuntime();
        try {
            Process p = runtime.exec(command);
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }


}
