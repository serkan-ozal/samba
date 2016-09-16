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

import java.util.Random;

import tr.com.serkanozal.samba.cache.SambaCacheType;

public class SambaFieldBenchmark {

    private static final int FIELD_COUNT = 64;
    private static final int ITERATION_COUNT = 100000000;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
    
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws InterruptedException {
        final SambaField<String>[] sambaFields = new SambaField[FIELD_COUNT];
        for (int i = 0; i < sambaFields.length; i++) {
            SambaField<String> sambaField = 
                    new SambaField<String>("SambaField-" + i, SambaCacheType.LOCAL);
            sambaField.set("Hello Samba " + i);
            sambaFields[i] = sambaField;
        }
        
        System.out.println("Warmup will start in 5 seconds ...");
        Thread.sleep(5000);
        doBenchmark(sambaFields, ITERATION_COUNT);
        
        System.out.println("Benchmark will start in 3 seconds ...");
        Thread.sleep(1000);
        
        Thread mutator = new Thread() {
            @Override
            public void run() {
                Random random = new Random();
                for (;;) {
                    int fieldNo = random.nextInt(sambaFields.length);
                    sambaFields[fieldNo].set("Good bye Samba " + fieldNo);
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        };
        mutator.setDaemon(true);
        mutator.start();
        
        long start = System.nanoTime();

        Thread[] threads = new Thread[THREAD_COUNT];
        for (int k = 0; k < threads.length; k++) {
            Thread thread = new Thread() {
                public void run() {
                    doBenchmark(sambaFields, ITERATION_COUNT); 
                };
            };
            thread.setPriority(Thread.MAX_PRIORITY);
            threads[k] = thread;   
            thread.start();
        }    
        for (int k = 0; k < threads.length; k++) {
            threads[k].join();
        }
        
        long passedMilis = (System.nanoTime() - start) / 1000000;
        System.out.println("Finished in " +  passedMilis + " milliseconds");
        System.out.println("Throughput: " + 
                ((long) ITERATION_COUNT * FIELD_COUNT * threads.length / passedMilis * 1000) + " ops/sec");
	}
    
    private static void doBenchmark(final SambaField<String>[] sambaFields, final int iterationCount) {
        int fieldCount = sambaFields.length;
        for (int i = 0; i < iterationCount; i++) {
            for (int j = 0; j < fieldCount; j++) {
                sambaFields[j].get();
            } 
        } 
    }

}
