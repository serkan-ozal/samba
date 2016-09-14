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

import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheProvider;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public abstract class BaseSambaFieldTest {

    protected abstract SambaCacheType getCacheType();
    
    @Test
    public void test_fieldConsistency() {
        SambaCacheType cacheType = getCacheType();
        SambaCache cache = SambaCacheProvider.getCache(cacheType);
        
        cache.clear();
        
        try {
            String fieldId = UUID.randomUUID().toString();
            SambaField<String> field1 = new SambaField<String>(fieldId, cache);
            SambaField<String> field2 = new SambaField<String>(fieldId, cache);
            
            ////////////////////////////////////////////////////////// 
            
            Assert.assertNull(field1.get());
            Assert.assertNull(field2.get());
            
            ////////////////////////////////////////////////////////// 
            
            field1.set("Value-1");
            Assert.assertEquals("Value-1", field1.get());
            Assert.assertEquals("Value-1", field2.get());
            
            field1.set("Value-2");
            Assert.assertEquals("Value-2", field1.get());
            checkConsistency(field2, "Value-2");
            
            ////////////////////////////////////////////////////////// 
            
            field1.set(null);
            Assert.assertNull(field1.get());
            checkConsistency(field2, null);
            
            ////////////////////////////////////////////////////////// 
            
            Assert.assertFalse(field1.compareAndSet("Value-0", "Value-3"));
            Assert.assertNull(field1.get());
            Assert.assertNull(field2.get());
            
            Assert.assertTrue(field1.compareAndSet(null, "Value-3"));
            Assert.assertEquals("Value-3", field1.get());
            checkConsistency(field2, "Value-3");
            
            ////////////////////////////////////////////////////////// 

            Assert.assertFalse(field1.compareAndSet("Value-0", "Value-4"));
            Assert.assertEquals("Value-3", field1.get());
            Assert.assertEquals("Value-3", field2.get());
            
            Assert.assertTrue(field1.compareAndSet("Value-3", "Value-4"));
            Assert.assertEquals("Value-4", field1.get());
            checkConsistency(field2, "Value-4");
            
            ////////////////////////////////////////////////////////// 
            
            Assert.assertFalse(field1.compareAndSet("Value-0", null));
            Assert.assertEquals("Value-4", field1.get());
            Assert.assertEquals("Value-4", field2.get());
            
            Assert.assertTrue(field1.compareAndSet("Value-4", null));
            Assert.assertNull(field1.get());
            checkConsistency(field2, null);
            
            ////////////////////////////////////////////////////////// 
            
            field1.set("Value-5");
            Assert.assertEquals("Value-5", field1.get());
            checkConsistency(field2, "Value-5");
            
            ////////////////////////////////////////////////////////// 
            
            field1.clear();
            Assert.assertNull(field1.get());
            checkConsistency(field2, null);
            
            ////////////////////////////////////////////////////////// 
            
            field1.set("Value-6");
            Assert.assertEquals("Value-6", field1.get());
            checkConsistency(field2, "Value-6");
            
            ////////////////////////////////////////////////////////// 
            
            cache.clear();
            Assert.assertNull(field1.get());
            checkConsistency(field2, null);
        } finally {
            cache.clear();
        }    
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
                    if (expectedValue == null) {
                        if (field.get() == null) {
                            return;
                        }
                    } else {
                        if (expectedValue.equals(field.get())) {
                            return;
                        }
                    }    
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
                throw new AssertionFailedError(
                        String.format("Expected value %s couldn't be retrieved eventually!", expectedValue));
            default:
                throw new IllegalArgumentException("Unknown consistency model: " + consistencyModel);
        }
        
    }
    
}
