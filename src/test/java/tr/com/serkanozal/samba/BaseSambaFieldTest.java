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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheProvider;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public abstract class BaseSambaFieldTest {

    protected SambaCacheType cacheType;
    protected SambaCache cache1;
    protected SambaCache cache2;
    
    @Before
    public void setup() {
        cacheType = getCacheType();
        cache1 = SambaCacheProvider.createCache(cacheType);
        cache1.clear();
        cache2 = cacheType != SambaCacheType.LOCAL 
                    ? SambaCacheProvider.createCache(cacheType)
                    : cache1;
        cache2.clear();
    }
    
    @After
    public void tearDown() {
        cacheType = null;
        cache1.clear();
        cache1 = null;
        cache2.clear();
        cache2 = null;
    }
    
    protected abstract SambaCacheType getCacheType();
    
    @Test
    public void test_fieldConsistency() {
        String fieldId = UUID.randomUUID().toString();
        SambaField<String> field1 = new SambaField<String>(fieldId, cache1);
        SambaField<String> field2 = new SambaField<String>(fieldId, cache2);
        
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
        Assert.assertEquals("Value-5", field2.refresh());
        
        ////////////////////////////////////////////////////////// 
        
        field1.clear();
        Assert.assertNull(field1.get());
        checkConsistency(field2, null);
        
        ////////////////////////////////////////////////////////// 
        
        final AtomicBoolean createCalled1 = new AtomicBoolean(false);
        String value1 = field1.getOrCreate(new SambaValueFactory<String>() {
            @Override
            public void destroy(String value) {
            }
            
            @Override
            public String create() {
                createCalled1.set(true);
                return "Value-6";
            }
        }); 
        Assert.assertTrue(createCalled1.get());
        Assert.assertEquals("Value-6", value1);
        Assert.assertEquals("Value-6", field1.get());
        Assert.assertEquals("Value-6", field2.refresh());
        
        ////////////////////////////////////////////////////////// 
        
        final AtomicBoolean createCalled2 = new AtomicBoolean(false);
        String value2 = field1.getOrCreate(new SambaValueFactory<String>() {
            @Override
            public void destroy(String value) {
            }
            
            @Override
            public String create() {
                createCalled2.set(true);
                return "Value-7";
            }
        }); 
        Assert.assertFalse(createCalled2.get());
        Assert.assertEquals("Value-6", value2);
        Assert.assertEquals("Value-6", field1.get());
        Assert.assertEquals("Value-6", field2.refresh());
        
        ////////////////////////////////////////////////////////// 
        
        cache1.clear();
        Assert.assertNull(field1.get());
        checkConsistency(field2, null);
        
        //////////////////////////////////////////////////////////
                
        cache2.clear();
        Assert.assertNull(field1.get());
        checkConsistency(field2, null);
    }
    
    @Test
    public void test_fieldProcessor() {
        String fieldId = UUID.randomUUID().toString();
        SambaField<Integer> field1 = new SambaField<Integer>(fieldId, cache1);
        SambaField<Integer> field2 = new SambaField<Integer>(fieldId, cache2);
        
        ////////////////////////////////////////////////////////// 
        
        Assert.assertNull(field1.get());
        Assert.assertNull(field2.get());
        
        ////////////////////////////////////////////////////////// 
        
        SambaFieldProcessor<Integer> incrementer = 
                new SambaFieldProcessor<Integer>() {
                    @Override
                    public Integer process(Integer currentValue) {
                        if (currentValue == null) {
                            return 1;
                        } else {
                            return currentValue + 1;
                        }
                    }
                };
        
        for (int i = 0; i < 100; i++) {
            field1.process(incrementer);
        }
        
        Assert.assertEquals(100, field1.get().intValue());
        checkConsistency(field2, 100);
    }
    
    @Test
    public void test_atomicFieldProcessor() throws InterruptedException {
        String fieldId = UUID.randomUUID().toString();
        SambaField<Integer> field1 = new SambaField<Integer>(fieldId, cache1);
        SambaField<Integer> field2 = new SambaField<Integer>(fieldId, cache2);
        
        ////////////////////////////////////////////////////////// 
        
        Assert.assertNull(field1.get());
        Assert.assertNull(field2.get());
        
        ////////////////////////////////////////////////////////// 

        final SambaFieldProcessor<Integer> incrementer = 
                new SambaFieldProcessor<Integer>() {
                    @Override
                    public Integer process(Integer currentValue) {
                        if (currentValue == null) {
                            return 1;
                        } else {
                            return currentValue + 1;
                        }
                    }
                };
        
        Thread[] threads = new Thread[10];
        final CyclicBarrier barrier = new CyclicBarrier(threads.length);
        
        for (int i = 0; i < threads.length; i++) {
            final SambaField<Integer> field = i % 2 == 0 ? field1 : field2;
            threads[i] = new Thread() {
                public void run() {
                    try {
                        barrier.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                    for (int j = 0; j < 10; j++) {
                        field.processAtomically(incrementer);
                    }
                };
            };
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }

        checkConsistency(field1, 100);
        checkConsistency(field2, 100);
    }
    
    private void checkConsistency(SambaField<?> field, Object expectedValue) {
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
