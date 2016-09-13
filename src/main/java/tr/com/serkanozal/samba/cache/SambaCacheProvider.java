package tr.com.serkanozal.samba.cache;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import tr.com.serkanozal.samba.cache.impl.SambaGlobalCache;
import tr.com.serkanozal.samba.cache.impl.SambaLocalCache;
import tr.com.serkanozal.samba.cache.impl.SambaTieredCache;

public class SambaCacheProvider {

    private static final Map<SambaCacheType, SambaCache> CACHE_MAP = 
            new HashMap<SambaCacheType, SambaCache>(SambaCacheType.values().length);
    
    static {
        CACHE_MAP.put(SambaCacheType.LOCAL, new SambaLocalCache());
        CACHE_MAP.put(SambaCacheType.GLOBAL, new SambaGlobalCache());
        CACHE_MAP.put(SambaCacheType.TIERED, new SambaTieredCache());
    }
    
    private SambaCacheProvider() {
        
    }
    
    public static SambaCache getCache(SambaCacheType cacheType) {
        SambaCache cache = CACHE_MAP.get(cacheType);
        if (cache == null) {
            throw new IllegalArgumentException("Unknow cache type: " + cacheType + 
                    "! Valid values are " + Arrays.asList(SambaCacheType.values()));
        }
        return cache;
    }
    
}
