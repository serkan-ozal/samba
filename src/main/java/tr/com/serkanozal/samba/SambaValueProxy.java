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

public final class SambaValueProxy {

    public static final Object INVALIDATED = new Object();
    
    private volatile Object value;
    
    public SambaValueProxy() {
    }
    
    public SambaValueProxy(Object value) {
        this.value = value;
    }
    
    public Object getValue() {
        return value;
    }

    public void invalidateValue() {
        value = INVALIDATED;
    }
    
    public boolean isInvalid() {
        return value != INVALIDATED;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SambaValueProxy)) {
            return false;
        }
        SambaValueProxy proxy = (SambaValueProxy) obj;
        if (value == proxy.value) {
            return true;
        }
        if ((value != null && proxy.value == null) 
                || (value == null && proxy.value != null)) {
            return false;
        }
        return value.equals(proxy.value);
    }
    
}
