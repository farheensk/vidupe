package vidupe.frontend;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonIgnoreProperties("hashes")
public class VideoHashesInformation {
    String videoID;
    String videoName;
    long duration;
    long numberOfKeyFrames;
    List<List<String>> hashes;

}
