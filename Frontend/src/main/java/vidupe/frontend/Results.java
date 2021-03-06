package vidupe.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.gax.paging.Page;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vidupe.constants.Constants;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


@WebServlet("/Results")
public class Results extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(Results.class);

    public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String jobId = request.getParameter("jobid");
        String email = request.getParameter("email");
        logger.info("Waiting for results: jobId:" + jobId + " ,email:  " + email);
        VidupeStoreManager vidupeStoreManager = new VidupeStoreManager(DatastoreOptions.newBuilder().setNamespace(Constants.NAMESPACE).build().getService());
        logger.info("Waiting for results: jobId:" + jobId + " ,email:  " + email);
        boolean isServletNeedRefresh = true;
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        try {
            boolean checkIfDeDupeDone = vidupeStoreManager.checkIfDeDupeModuleDone(email, jobId);
            if (checkIfDeDupeDone) {
                logger.info("Displaying results: jobId:" + jobId + " ,email:  " + email);
                isServletNeedRefresh = false;
                Storage storage = StorageOptions.getDefaultInstance().getService();
                String directory = email + "/" + jobId;
                //  Blob blob = storage.get("vidupe", email + "/" + jobId);
                Page<Blob> blobs = storage.list(Constants.BUCKET_NAME, Storage.BlobListOption.currentDirectory(),
                        Storage.BlobListOption.prefix(directory));
                System.out.println(blobs.iterateAll());
                for (Blob blob : blobs.iterateAll()) {
                    System.out.println(blob.getName());
//                if (blob != null && blob.exists()) {
                    System.out.println(blob.isDirectory());
                    if ((blob != null) && blob.isDirectory()) {
                        String path = directory + "/";
                        Page<Blob> results = storage.list(Constants.BUCKET_NAME, Storage.BlobListOption.currentDirectory(),
                                Storage.BlobListOption.prefix(path));
                        ArrayList<DuplicateVideosList> list = new ArrayList<>();
                        for (Blob b : results.iterateAll()) {
                            DuplicateVideosList duplicateVideosList = getDuplicateVideosList(b);
                            System.out.println(duplicateVideosList);
                            if (duplicateVideosList.getDuplicateVideosList() != null) {
                                if (duplicateVideosList.getDuplicateVideosList().size() > 0) {
                                    list.add(duplicateVideosList);
                                }
                            }
                        }
                        System.out.println(list);
                        if (list.size() > 0) {
                            String accessToken = vidupeStoreManager.getAccessTokenOfUser(jobId, email);
                            HashMap<String, HashMap<String, String>> thumbnailsSizesDurations = getThumbnailUrl(accessToken);
                            CombinedDuplicatesList combinedDuplicatesList = CombinedDuplicatesList.builder()
                                    .duplicateVideosList(list)
                                    .thumbnails(thumbnailsSizesDurations.get("thumbnails"))
                                    .build();
                            String duplicates = combinedDuplicatesList.toJsonString();
                            System.out.println(duplicates);
                            request.setAttribute("email", email);
                            request.setAttribute("jobId", jobId);
                            request.setAttribute("results", duplicates);
                            request.getRequestDispatcher("/resultsDisplay.jsp").forward(request, response);

                        } else {
                            request.getRequestDispatcher("/noDuplicatesPage.jsp").forward(request, response);
                        }
                    } else if ((blob != null) && blob.exists()) {
                        ByteString content = ByteString.copyFrom(blob.getContent());
                        String messageString = content.toStringUtf8();
                        if (messageString.equals("{}"))
                            request.getRequestDispatcher("/noDuplicatesPage.jsp").forward(request, response);
                        else if (messageString.equals("401")) {
                            request.getRequestDispatcher("/somethingWentWrongPage.jsp").forward(request, response);
                        }
                    }
                    // }
//
                }
            } else {
                out.println("<html><head>" +
                        "<title>Results</title>" +
                        "<link rel='stylesheet' href='style.css'>" +
                        "</head><body>" +
                        "<div class='background-top'>" +
                        "<h1 class='displayText'>ViDupe- Duplicate Video Detection Service</h1>" +
                        "</div>" +
                        "<div>");
                isServletNeedRefresh = true;
                String loadingGif = "loading.gif";
                out.println("<h3>Loading your results...please wait...</h3>");
                out.println("<img src='" + loadingGif + "' style='max-height:150px;max-width:150px;align:middle;'></img>");
                out.println("</body></html>");
            }
            if (isServletNeedRefresh)
                response.setIntHeader("Refresh", 5);
        }
        catch (Exception e){
            logger.info("Exception",e);
            request.getRequestDispatcher("/somethingWentWrongPage.jsp").forward(request, response);
        }
        out.close();
    }

    public DuplicateVideosList getDuplicateVideosList(Blob b) throws IOException {
        ByteString content = ByteString.copyFrom(b.getContent());
        String messageString = content.toStringUtf8();
        if (messageString.equals("{}"))
            return null;
        else {
            ObjectMapper mapper = new ObjectMapper();
            DuplicateVideosList duplicateVideosList = mapper.readValue(messageString, DuplicateVideosList.class);
            return duplicateVideosList;
        }
    }

    public HashMap<String,HashMap<String, String>> getThumbnailUrl(String accessToken) {
        HashMap<String, HashMap<String, String>> thumbnailsAndSizes = new HashMap<>();
        final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE,
                DriveScopes.DRIVE_PHOTOS_READONLY,
                DriveScopes.DRIVE_FILE,
                DriveScopes.DRIVE_METADATA,
                DriveScopes.DRIVE_METADATA_READONLY,
                DriveScopes.DRIVE_APPDATA,
                DriveScopes.DRIVE_READONLY);
        GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken).createScoped(SCOPES);
        Drive drive = new Drive.Builder(new NetHttpTransport(), JacksonFactory.getDefaultInstance(), credential)
                .setApplicationName("Duplicate video Detection").build();
        HashMap<String, String> thumbnails = new HashMap<>();
        HashMap<String, String> sizes = new HashMap<>();
        HashMap<String, String> durations = new HashMap<>();
        try {
            FileList result = drive.files().list().setFields(
                    "files(capabilities/canDownload,id,md5Checksum,mimeType,thumbnailLink,name,size,videoMediaMetadata,webContentLink)")
                    .execute();
            List<File> files = result.getFiles();
            for (File f : files) {
                thumbnails.put(f.getId(), f.getThumbnailLink());
                 Long videoString = f.getSize();
                 File.VideoMediaMetadata videoMediaMetadata = f.getVideoMediaMetadata();
                if(videoString !=null && videoMediaMetadata !=null){
                    String videoSizeString = humanReadableByteCount(videoString, true);
                    sizes.put(f.getId(), videoSizeString);
                    Long durationMillis = videoMediaMetadata.getDurationMillis();
                    String durationString = getDurationAsString(durationMillis);
                    durations.put(f.getId(),durationString);
                }

            }
        }
        catch (Exception e){
            System.out.println(e);


        }
        thumbnailsAndSizes.put("thumbnails",thumbnails);
        thumbnailsAndSizes.put("sizes",sizes);
        thumbnailsAndSizes.put("durations",durations);
        return thumbnailsAndSizes;
    }

    private String getDurationAsString(Long durationMillis) {
        String vTime = "null";
        if(durationMillis!=null){
            vTime = String.format("%02d min, %02d sec",
                    TimeUnit.MILLISECONDS.toMinutes(durationMillis),
                    TimeUnit.MILLISECONDS.toSeconds(durationMillis) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(durationMillis))
            );
        }
       return vTime;
    }

    public String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

}