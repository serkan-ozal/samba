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

import org.apache.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import tr.com.serkanozal.samba.SambaValueProxy;
import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public class SambaLocalCache implements SambaCache {

    private static final Logger LOGGER = Logger.getLogger(SambaLocalCache.class);
    
    private final NonBlockingHashMap<String, Object> map = 
            new NonBlockingHashMap<String, Object>();
    
    @Override
    public SambaCacheType getType() {
        return SambaCacheType.LOCAL;
    }
    
    @Override
    public SambaCacheConsistencyModel getConsistencyModel() {
        return SambaCacheConsistencyModel.STRONG_CONSISTENCY;
    }
    
    @Override
    public boolean supportInvalidation() {
        return true;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <V> V get(String key) {
        V value = (V) map.get(key);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been retrieved from local cache with key %s", key, value));
        }
        return value;
    }

    @Override
    public void put(String key, Object value) {
        Object oldValue = map.put(key, value);
        if (oldValue instanceof SambaValueProxy) {
            ((SambaValueProxy) oldValue).invalidateValue();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been put into local cache with key %s", key, value));
        }
    }

    @Override
    public void remove(String key) {
        Object oldValue = map.remove(key);
        if (oldValue instanceof SambaValueProxy) {
            ((SambaValueProxy) oldValue).invalidateValue();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value has been removed from local cache with key %s", key));
        }
    }

}
