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

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.cliffc.high_scale_lib.NonBlockingHashMap;

import tr.com.serkanozal.samba.SambaValueProxy;
import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public class SambaLocalCache implements SambaCache {

    private static final Logger LOGGER = Logger.getLogger(SambaLocalCache.class);
    
    private final NonBlockingHashMap<String, LocalValueWrapper> map = 
            new NonBlockingHashMap<String, LocalValueWrapper>();
    
    @Override
    public SambaCacheType getType() {
        return SambaCacheType.LOCAL;
    }
    
    @Override
    public SambaCacheConsistencyModel getConsistencyModel() {
        return SambaCacheConsistencyModel.STRONG_CONSISTENCY;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object get(String key) {
        Object value = null;
        SambaValueProxy valueProxy = unwrapValue(map.get(key));
        if (valueProxy != null) {
            value = valueProxy.getValue();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been retrieved from local cache with key %s", key, value));
        }
        return valueProxy;
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            remove(key);
        } else {
            SambaValueProxy oldValueProxy = unwrapValue(map.put(key, wrapValue(new SambaValueProxy(value))));
            if (oldValueProxy != null) {
                oldValueProxy.invalidateValue();
            }
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        String.format("Value %s has been put into local cache with key %s", key, value));
            }
        }    
    }

    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        boolean replaced = false;
        if (oldValue == null && newValue != null) {
            if (map.putIfAbsent(key, wrapValue(new SambaValueProxy(newValue))) == null) {
                replaced = true;
            }
        } else if (oldValue != null && newValue == null) {
            LocalValueWrapper oldValueWraper = wrapValue(new SambaValueProxy(oldValue));
            replaced = map.remove(key, oldValueWraper);
            if (replaced) {
                assert oldValueWraper.equalValueWrapper != null;
                
                SambaValueProxy oldValueProxy = oldValueWraper.equalValueWrapper.value;
                if (oldValueProxy != null) {
                    oldValueProxy.invalidateValue();
                }
            }
        } else if (oldValue != null && newValue != null) {
            LocalValueWrapper oldValueWraper = wrapValue(new SambaValueProxy(oldValue));
            LocalValueWrapper newValueWrapper = wrapValue(new SambaValueProxy(newValue));
            replaced = map.replace(key, oldValueWraper, newValueWrapper);
            if (replaced) {
                assert oldValueWraper.equalValueWrapper != null;
                
                SambaValueProxy oldValueProxy = oldValueWraper.equalValueWrapper.value;
                if (oldValueProxy != null) {
                    oldValueProxy.invalidateValue();
                }
            }
        }    
        if (replaced && LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Old value %s has been replaced with new value %s " + 
                                  "assigned to key %s", oldValue, newValue, key));
        }
        return replaced;
    }

    @Override
    public void remove(String key) {
        SambaValueProxy oldValueProxy = unwrapValue(map.remove(key));
        if (oldValueProxy != null) {
            oldValueProxy.invalidateValue();
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value has been removed from local cache with key %s", key));
        }
    }
    
    @Override
    public void clear() {
        Iterator<String> iter = map.keySet().iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            remove(key);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Local cache has been cleared");
        }
    }
    
    private LocalValueWrapper wrapValue(SambaValueProxy valueProxy) {
        return new LocalValueWrapper(valueProxy);
    }
    
    private SambaValueProxy unwrapValue(LocalValueWrapper wrapper) {
        if (wrapper != null) {
            return (SambaValueProxy) wrapper.value;
        } else {
            return null;
        }
    }
    
    private static final class LocalValueWrapper {
        
        private final SambaValueProxy value;
        private LocalValueWrapper equalValueWrapper;
        
        private LocalValueWrapper(SambaValueProxy value) {
            this.value = value;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof LocalValueWrapper)) {
                return false;
            }
            LocalValueWrapper wrapper = (LocalValueWrapper) obj;
            if (value == wrapper.value) {
                return true;
            }
            if ((value != null && wrapper.value == null) 
                    || (value == null && wrapper.value != null)) {
                return false;
            }
            boolean equals = value.equals(wrapper.value);
            if (equals) {
                equalValueWrapper = (LocalValueWrapper) obj;
            }
            return equals;
        }
        
    }

}
