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
    
    @Override
    public boolean doesSupportInvalidation() {
        return true;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public <V> V get(String key) {
        V value = (V) unwrapValue(map.get(key));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been retrieved from local cache with key %s", key, value));
        }
        return value;
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            remove(key);
        } else {
            Object oldValue = unwrapValue(map.put(key, wrapValue(value)));
            if (oldValue instanceof SambaValueProxy) {
                ((SambaValueProxy) oldValue).invalidateValue();
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
            if (map.putIfAbsent(key, wrapValue(newValue)) == null) {
                replaced = true;
            }
        } else if (oldValue != null && newValue == null) {
            LocalValueWrapper oldValueWraper = wrapValue(oldValue);
            replaced = map.remove(key, oldValueWraper);
            if (replaced) {
                assert oldValueWraper.equalValueWrapper != null;
                
                Object oldValueRef = oldValueWraper.equalValueWrapper.value;
                if (oldValueRef instanceof SambaValueProxy) {
                    ((SambaValueProxy) oldValueRef).invalidateValue();
                }
            }
        } else if (oldValue != null && newValue != null) {
            LocalValueWrapper oldValueWraper = wrapValue(oldValue);
            LocalValueWrapper newValueWrapper = wrapValue(newValue);
            replaced = map.replace(key, oldValueWraper, newValueWrapper);
            if (replaced) {
                assert oldValueWraper.equalValueWrapper != null;
                
                Object oldValueRef = oldValueWraper.equalValueWrapper.value;
                if (oldValueRef instanceof SambaValueProxy) {
                    ((SambaValueProxy) oldValueRef).invalidateValue();
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
        Object oldValue = unwrapValue(map.remove(key));
        if (oldValue instanceof SambaValueProxy) {
            ((SambaValueProxy) oldValue).invalidateValue();
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
    
    private LocalValueWrapper wrapValue(Object value) {
        return new LocalValueWrapper(value);
    }
    
    private Object unwrapValue(LocalValueWrapper wrapper) {
        if (wrapper != null) {
            return wrapper.value;
        } else {
            return null;
        }
    }
    
    private static final class LocalValueWrapper {
        
        private final Object value;
        private LocalValueWrapper equalValueWrapper;
        
        private LocalValueWrapper(Object value) {
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
