/*
 * Copyright (c) 2016, Serkan OZAL, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    
    public static SambaCache createCache(SambaCacheType cacheType) {
        switch (cacheType) {
            case LOCAL:
                return new SambaLocalCache();
            case GLOBAL:
                return new SambaGlobalCache();
            case TIERED:
                return new SambaTieredCache();
            default:
                throw new IllegalArgumentException("Unknow cache type: " + cacheType + 
                        "! Valid values are " + Arrays.asList(SambaCacheType.values()));
        }
    }
    
}
