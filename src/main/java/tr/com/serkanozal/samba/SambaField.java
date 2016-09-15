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
package tr.com.serkanozal.samba;

import java.util.UUID;

import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheProvider;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public class SambaField<T> {

    private static final SambaValueProxy EMPTY_PROXY = 
            new SambaValueProxy(SambaValueProxy.INVALIDATED);
    
    private final SambaCache cache;
    private final String id;
    private SambaValueProxy valueProxy;
    
    public SambaField(SambaCacheType cacheType) {
        this(generateIdFromCallee(), cacheType);
    }
    
    private static String generateIdFromCallee() {
        StackTraceElement callee = Thread.currentThread().getStackTrace()[3];
        String calleeId = callee.getClassName() + "#" + callee.getMethodName() + ":" + callee.getLineNumber();
        return UUID.nameUUIDFromBytes(calleeId.getBytes()).toString();
    }

    public SambaField(String id, SambaCacheType cacheType) {
        this(id, SambaCacheProvider.getCache(cacheType));
    }
    
    public SambaField(String id, SambaCache cache) {
        this.id = id;
        this.cache = cache;
        this.valueProxy = EMPTY_PROXY;
    }
    
    public String getId() {
        return id;
    }
    
    public SambaCache getCache() {
        return cache;
    }
    
    public SambaCacheConsistencyModel getConsistencyModel() {
        return cache.getConsistencyModel();
    }
    
    @SuppressWarnings("unchecked")
    public T get() {
        Object value = valueProxy.getValue();
        if (value != SambaValueProxy.INVALIDATED) {
            return (T) value;
        }  
        for (;;) {
            value = cache.get(id);
            if (value instanceof SambaValueProxy) {
                SambaValueProxy proxy = (SambaValueProxy) value;
                if (proxy != null) {
                    valueProxy = proxy;
                    value = valueProxy.getValue();
                    if (value != SambaValueProxy.INVALIDATED) {
                        return (T) value;
                    }    
                }
            } else {
                valueProxy = EMPTY_PROXY;
                return (T) value;
            }
        }    
    }
    
    @SuppressWarnings("unchecked")
    public T refresh() {
        valueProxy = EMPTY_PROXY;
        for (;;) {
            Object value = cache.refresh(id);
            if (value instanceof SambaValueProxy) {
                SambaValueProxy proxy = (SambaValueProxy) value;
                if (proxy != null) {
                    valueProxy = proxy;
                    value = valueProxy.getValue();
                    if (value != SambaValueProxy.INVALIDATED) {
                        return (T) value;
                    }    
                }
            } else {
                valueProxy = EMPTY_PROXY;
                return (T) value;
            }
        }    
    }
    
    public void set(T value) {
        if (value == null) {
            clear();
        } else {
            cache.put(id, value);
        }    
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public boolean compareAndSet(T oldValue, T newValue) {
        return cache.replace(id, oldValue, newValue);
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public boolean compareAndSet(T newValue) {
        return compareAndSet(get(), newValue);
    }
    
    public void clear() {
        cache.remove(id);
        // TODO Also clear proxy on update eagerly as atomic
    }
    
    public T process(SambaFieldProcessor<T> processor) {
        T currentValue = get();
        T newValue = processor.process(currentValue);
        set(newValue);
        return newValue;
    }
    
    public T processAtomically(SambaFieldProcessor<T> processor) {
        T currentValue = get();
        for (;;) {
            T newValue = processor.process(currentValue);
            if (compareAndSet(currentValue, newValue)) {
                return newValue;
            }
            currentValue = refresh();
        }
    }

    @Override
    public String toString() {
        return "SambaField [" + 
                    "cacheType=" + cache.getType() + 
                    ", id=" + id + 
                    ", value=" + get() + 
               "]";
    }

}
