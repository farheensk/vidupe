package vidupe.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.List;

@Data
@Builder
public class DuplicateVideosList {
    private VideoHashesInformation referenceVideo;
    private List<VideoHashesInformation> duplicateVideosList;
    private HashMap<String,String> bestVideoIds;
    public byte[] toBytes() {
        ObjectMapper objectMapper = new ObjectMapper();
        byte[] value = null;
        try {
            value = objectMapper.writeValueAsBytes(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return value;
    }

    public String toJsonString() {
        ObjectMapper objectMapper = new ObjectMapper();
        String value = "";
        try {
            value = objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return value;
    }
}
