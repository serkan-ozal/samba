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
package tr.com.serkanozal.samba.cache.impl;

import java.util.concurrent.atomic.AtomicLongArray;

import org.apache.log4j.Logger;

import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheType;
import tr.com.serkanozal.samba.cache.impl.SambaGlobalCache.CacheChangeListener;

public class SambaTieredCache implements SambaCache {

    private static final Logger LOGGER = Logger.getLogger(SambaTieredCache.class);
    
    private final NearCache nearCache;
    private final SambaGlobalCache globalCache;
    
    public SambaTieredCache() {
        nearCache = new NearCache(new SambaLocalCache());
        globalCache = new SambaGlobalCache(new CacheChangeListener() {
            private void invalidate(String key) {
                long ownId = nearCache.tryOwn(key);
                try {
                    nearCache.remove(key);
                } finally {
                    nearCache.releaseIfOwned(ownId, key);
                }
            }
            
            @Override
            public void onInsert(String key, Object value) {
                invalidate(key);
            }
            
            @Override
            public void onUpdate(String key, Object oldValue, Object newValue) {
                invalidate(key);
            }

            @Override
            public void onDelete(String key) {
                invalidate(key);
            }
        });
    }
    
    @Override
    public SambaCacheType getType() {
        return SambaCacheType.TIERED;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <V> V get(String key) {
        V value = (V) nearCache.get(key);
        if (value != null) {
            return value;
        }
        
        long ownId = nearCache.tryOwn(key);
        try {
            value = globalCache.get(key);
            if (value != null) {
                nearCache.putIfAvailable(ownId, key, value);
            }    
        } finally {
            nearCache.releaseIfOwned(ownId, key);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been retrieved from tiered cache with key %s", key, value));
        }
        
        return value;
    }

    @Override
    public void put(String key, Object value) {
        long ownId = nearCache.tryOwn(key);
        try {
            globalCache.put(key, value);
            nearCache.putIfAvailable(ownId, key, value);  
        } finally {
            nearCache.releaseIfOwned(ownId, key);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been put into tiered cache with key %s", key, value));
        }
    }

    @Override
    public void remove(String key) {
        long ownId = nearCache.tryOwn(key);
        try {
            globalCache.remove(key);
            nearCache.remove(key);
        } finally {
            nearCache.releaseIfOwned(ownId, key);
        }
        
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value has been removed from tiered cache with key %s", key));
        }
    }
    
    private class NearCache {

        private final int SLOT_COUNT = 1024;
        private final int SLOT_MASK = SLOT_COUNT - 1;
        
        private final AtomicLongArray slotStates = new AtomicLongArray(SLOT_COUNT * 4);
        private final SambaCache localCache;
        
        private NearCache(SambaCache localCache) {
            this.localCache = localCache;
        }
        
        private int getSlot(String key) {
            int hash = key.hashCode();
            return hash & SLOT_MASK;
        }
        
        private int ownIdIndex(int slot) {
            return (slot << 2);
        }
        
        private int activeCountIndex(int slot) {
            return (slot << 2) + 1;
        }
        
        private int completedCountIndex(int slot) {
            return (slot << 2) + 2;
        }

        private long tryOwn(String key) {
            long ownId = -1;
            int slot = getSlot(key);
            long currentCompleted = slotStates.get(completedCountIndex(slot));
            if (slotStates.compareAndSet(ownIdIndex(slot), 0, currentCompleted)) {
                ownId = currentCompleted;
            }
            slotStates.incrementAndGet(activeCountIndex(slot));
            return ownId;
        }

        private void releaseIfOwned(long ownId, String key) {
            int slot = getSlot(key);
            slotStates.incrementAndGet(completedCountIndex(slot));
            slotStates.decrementAndGet(activeCountIndex(slot));
            if (ownId >= 0) {
                slotStates.set(ownIdIndex(slot), 0);
            }   
        }

        private Object get(String key) {
            return localCache.get(key);
        }

        private void put(String key, Object value) {
            localCache.put(key, value);
        }

        private void remove(String key) {
            localCache.remove(key);
        }

        private void putIfAvailable(long ownId, String key, Object value) {
            if (ownId >= 0) {
                int slot = getSlot(key);
                long activeCount = slotStates.get(activeCountIndex(slot));
                long expectedCompleted = ownId;
                long currentCompleted = slotStates.get(completedCountIndex(slot));
                if (activeCount == 1 && currentCompleted == expectedCompleted) {
                    put(key, value);
                }   
            }
        }

    }   

}
