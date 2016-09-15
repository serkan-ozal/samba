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

public class SambaField<V> {

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
    public V get() {
        Object value = valueProxy.getValue();
        if (value != SambaValueProxy.INVALIDATED) {
            return (V) value;
        }  
        for (;;) {
            value = cache.get(id);
            if (value instanceof SambaValueProxy) {
                SambaValueProxy proxy = (SambaValueProxy) value;
                if (proxy != null) {
                    valueProxy = proxy;
                    value = valueProxy.getValue();
                    if (value != SambaValueProxy.INVALIDATED) {
                        return (V) value;
                    }    
                }
            } else {
                valueProxy = EMPTY_PROXY;
                return (V) value;
            }
        }    
    }
    
    public V getOrCreate(SambaValueFactory<V> factory) {
        V value = get();
        if (value != null) {
            return value;
        } else {
            V createdValue = factory.create();
            for (;;) {
                if (compareAndSet(null, createdValue)) {
                    return createdValue;
                } else {
                    value = refresh();
                    if (value != null) {
                        factory.destroy(createdValue);
                        return value;
                    }
                }
            }    
        }
    }
     
    @SuppressWarnings("unchecked")
    public V refresh() {
        valueProxy = EMPTY_PROXY;
        for (;;) {
            Object value = cache.refresh(id);
            if (value instanceof SambaValueProxy) {
                SambaValueProxy proxy = (SambaValueProxy) value;
                if (proxy != null) {
                    valueProxy = proxy;
                    value = valueProxy.getValue();
                    if (value != SambaValueProxy.INVALIDATED) {
                        return (V) value;
                    }    
                }
            } else {
                valueProxy = EMPTY_PROXY;
                return (V) value;
            }
        }    
    }
    
    public void set(V value) {
        if (value == null) {
            clear();
        } else {
            cache.put(id, value);
        }    
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public boolean compareAndSet(V oldValue, V newValue) {
        return cache.replace(id, oldValue, newValue);
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public boolean compareAndSet(V newValue) {
        return compareAndSet(get(), newValue);
    }
    
    public void clear() {
        cache.remove(id);
        // TODO Also clear proxy on update eagerly as atomic
    }
    
    public V process(SambaFieldProcessor<V> processor) {
        V currentValue = get();
        V newValue = processor.process(currentValue);
        set(newValue);
        return newValue;
    }
    
    public V processAtomically(SambaFieldProcessor<V> processor) {
        V currentValue = get();
        for (;;) {
            V newValue = processor.process(currentValue);
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
