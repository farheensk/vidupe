package vidupe.dedupe;

import java.util.ArrayList;
import java.util.List;

public class DurationFilter
{
    public List<VideoHashesInformation>  filterOutDurations(List<VideoHashesInformation> files)
    {
        files.sort(new MapComparator("duration"));
        List<VideoHashesInformation> returnSet = new ArrayList<>();
        int visit[] = new int[files.size()];

        for(int i=0;i<(files.size()-1);i++){
            final long longVideo = files.get(i).getDuration();
            if((longVideo - files.get(i+1).getDuration())<= longVideo*0.1){
                visit[i] = 1;
                visit[i+1]=1;
            }
        }
        for(int i=0;i<visit.length;i++){
            if(visit[i]==1){
                returnSet.add(files.get(i));
            }
        }
        return returnSet;
    }
}