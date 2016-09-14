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
    private final boolean proxyInvalidationAware;
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
        this.proxyInvalidationAware = cache.doesSupportInvalidation();
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
        SambaValueProxy proxy = cache.get(id);
        if (proxy != null) {
            valueProxy = proxy;
            value = valueProxy.getValue();
            if (!proxyInvalidationAware) {
                valueProxy.invalidateValue();
            }
            return (T) value;
        } else {
            valueProxy = EMPTY_PROXY;
            return null;
        }
    }
    
    public void set(T value) {
        if (value == null) {
            clear();
        } else {
            cache.put(id, new SambaValueProxy(value));
        }    
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public boolean compareAndSet(T oldValue, T newValue) {
        return cache.replace(id, new SambaValueProxy(oldValue), new SambaValueProxy(newValue));
        // TODO Also set proxy on update eagerly as atomic 
    }
    
    public void clear() {
        cache.remove(id);
        // TODO Also clear proxy on update eagerly as atomic
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
