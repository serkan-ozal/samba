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

import junit.framework.AssertionFailedError;

import org.junit.Assert;
import org.junit.Test;

import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public abstract class BaseSambaFieldTest {

    protected abstract SambaCacheType getCacheType();
    
    @Test
    public void test_fieldConsistency() {
        SambaCacheType cacheType = getCacheType();
        String fieldId = UUID.randomUUID().toString();
        SambaField<String> field1 = new SambaField<String>(fieldId, cacheType);
        SambaField<String> field2 = new SambaField<String>(fieldId, cacheType);
        
        field1.set("Value-x");
        Assert.assertEquals("Value-x", field2.get());
        
        field1.set("Value-y");
        checkConsistency(field2, "Value-y");
    }
    
    private void checkConsistency(SambaField<String> field, String expectedValue) {
        SambaCacheConsistencyModel consistencyModel = field.getConsistencyModel();
        switch (consistencyModel) {
            case STRONG_CONSISTENCY:
                Assert.assertEquals(expectedValue, field.get());
                break;
            case EVENTUAL_CONSISTENCY:
                long start = System.currentTimeMillis();
                long finish = start + 30 * 1000; // 30 seconds later
                while (System.currentTimeMillis() < finish) {
                    if (expectedValue.equals(field.get())) {
                        return;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                throw new AssertionFailedError(
                        String.format("Expected value %s couldn't be retrieved eventually!"));
            default:
                throw new IllegalArgumentException("Unknown consistency model: " + consistencyModel);
        }
        
    }
    
}
