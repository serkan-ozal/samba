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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreamsClient;
import com.amazonaws.services.dynamodbv2.document.Expected;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamRequest;
import com.amazonaws.services.dynamodbv2.model.DescribeStreamResult;
import com.amazonaws.services.dynamodbv2.model.DescribeTableResult;
import com.amazonaws.services.dynamodbv2.model.GetRecordsRequest;
import com.amazonaws.services.dynamodbv2.model.GetRecordsResult;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorRequest;
import com.amazonaws.services.dynamodbv2.model.GetShardIteratorResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.Record;
import com.amazonaws.services.dynamodbv2.model.ResourceInUseException;
import com.amazonaws.services.dynamodbv2.model.ResourceNotFoundException;
import com.amazonaws.services.dynamodbv2.model.Shard;
import com.amazonaws.services.dynamodbv2.model.ShardIteratorType;
import com.amazonaws.services.dynamodbv2.model.StreamRecord;
import com.amazonaws.services.dynamodbv2.model.StreamSpecification;
import com.amazonaws.services.dynamodbv2.model.StreamViewType;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.FastInput;
import com.esotericsoftware.kryo.io.FastOutput;

import tr.com.serkanozal.samba.cache.SambaCache;
import tr.com.serkanozal.samba.cache.SambaCacheConsistencyModel;
import tr.com.serkanozal.samba.cache.SambaCacheType;

public class SambaGlobalCache implements SambaCache {

    private static final Logger LOGGER = Logger.getLogger(SambaGlobalCache.class);
    
    private final String DYNAMO_DB_TABLE_NAME;
    private final int DYNAMO_DB_TABLE_READ_CAPACITY_PER_SECOND;
    private final int DYNAMO_DB_TABLE_WRITE_CAPACITY_PER_SECOND;
    private final AmazonDynamoDB DYNAMO_DB;
    private final Table DYNAMO_DB_TABLE;
    private final AmazonDynamoDBStreamsClient DYNAMO_DB_STREAMS;
    private final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = 
            Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                private final ThreadFactory delegatedThreadFactory = Executors.defaultThreadFactory();
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = delegatedThreadFactory.newThread(r);
                    t.setDaemon(true);
                    return t;
                }
            });
    private final ThreadLocal<ReusableKryo> threadLocalKryo = 
            new ThreadLocal<ReusableKryo>() {
                protected ReusableKryo initialValue() {
                    return new ReusableKryo();
                };
            };
    private final List<CacheChangeListener> cacheChangeListeners = 
            new CopyOnWriteArrayList<CacheChangeListener>();   
    private final String UUID = java.util.UUID.randomUUID().toString();
    
    public SambaGlobalCache() {
        this(null);
    }
   
    public SambaGlobalCache(CacheChangeListener cacheChangeListener) {
        try {
            Properties sambaProps = getProperties("samba.properties");
            String tableName = sambaProps.getProperty("cache.global.tableName");
            if (tableName != null) {
                DYNAMO_DB_TABLE_NAME = tableName;
            } else {
                DYNAMO_DB_TABLE_NAME = "___SambaGlobalCache___";
            }
            String readCapacityPerSecond = sambaProps.getProperty("cache.global.readCapacityPerSecond");
            if (readCapacityPerSecond != null) {
                DYNAMO_DB_TABLE_READ_CAPACITY_PER_SECOND = Integer.parseInt(readCapacityPerSecond);
            } else {
                DYNAMO_DB_TABLE_READ_CAPACITY_PER_SECOND = 1000;
            }
            String writeCapacityPerSecond = sambaProps.getProperty("cache.global.writeCapacityPerSecond");
            if (writeCapacityPerSecond != null) {
                DYNAMO_DB_TABLE_WRITE_CAPACITY_PER_SECOND = Integer.parseInt(writeCapacityPerSecond);
            } else {
                DYNAMO_DB_TABLE_WRITE_CAPACITY_PER_SECOND = 100;
            }
            
            /////////////////////////////////////////////////////////////////
            
            Properties awsProps = getProperties("aws-credentials.properties");
            AWSCredentials awsCredentials = 
                    new BasicAWSCredentials(
                            awsProps.getProperty("aws.accessKey"), 
                            awsProps.getProperty("aws.secretKey"));
            
            DYNAMO_DB = new AmazonDynamoDBClient(awsCredentials);
            DYNAMO_DB_STREAMS = new AmazonDynamoDBStreamsClient(awsCredentials);
            DYNAMO_DB_TABLE = ensureTableAvailable();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        if (cacheChangeListener != null) {
            registerCacheChangeListener(cacheChangeListener);
        }
    }
    
    interface CacheChangeListener {

        void onInsert(String key, Object value);
        void onUpdate(String key, Object oldValue, Object newValue);
        void onDelete(String key);
        
    }

    private Table ensureTableAvailable() {
        boolean tableExist = false;
        try {
            DYNAMO_DB.describeTable(DYNAMO_DB_TABLE_NAME);
            tableExist = true;
        } catch (ResourceNotFoundException e) {
        }
        
        if (!tableExist) {
            ArrayList<AttributeDefinition> attributeDefinitions = 
                    new ArrayList<AttributeDefinition>();
            attributeDefinitions.add(
                    new AttributeDefinition().
                            withAttributeName("id").
                            withAttributeType("S"));
    
            ArrayList<KeySchemaElement> keySchema = new ArrayList<KeySchemaElement>();
            keySchema.add(
                    new KeySchemaElement().
                            withAttributeName("id").
                            withKeyType(KeyType.HASH));
    
            StreamSpecification streamSpecification = new StreamSpecification();
            streamSpecification.setStreamEnabled(true);
            streamSpecification.setStreamViewType(StreamViewType.NEW_AND_OLD_IMAGES);
    
            CreateTableRequest createTableRequest = 
                    new CreateTableRequest().
                            withTableName(DYNAMO_DB_TABLE_NAME).
                            withKeySchema(keySchema).
                            withAttributeDefinitions(attributeDefinitions).
                            withStreamSpecification(streamSpecification).
                            withProvisionedThroughput(
                                    new ProvisionedThroughput().
                                            withReadCapacityUnits((long) DYNAMO_DB_TABLE_READ_CAPACITY_PER_SECOND).
                                            withWriteCapacityUnits((long) DYNAMO_DB_TABLE_WRITE_CAPACITY_PER_SECOND));
            
            try {
                LOGGER.info(
                        String.format(
                                "Creating DynamoDB table (%s) creation, because it is not exist", 
                                DYNAMO_DB_TABLE_NAME));
                
                DYNAMO_DB.createTable(createTableRequest);
            } catch (ResourceInUseException e) { 
                LOGGER.info(
                        String.format(
                                "Ignoring DynamoDB table (%s) creation, because it is already exist", 
                                DYNAMO_DB_TABLE_NAME));
            }
        } else {
            LOGGER.info(
                    String.format(
                            "Ignoring DynamoDB table (%s) creation, because it is already exist", 
                            DYNAMO_DB_TABLE_NAME));
        }
        
        while (true) {
            DescribeTableResult describeTableResult = 
                    DYNAMO_DB.describeTable(DYNAMO_DB_TABLE_NAME);
            TableDescription tableDescription = describeTableResult.getTable();
            if ("ACTIVE".equals(tableDescription.getTableStatus())) {
                break;
            }
            LOGGER.info(
                    String.format(
                            "DynamoDB table (%s) is not active yet, waiting until it is active ...", 
                            DYNAMO_DB_TABLE_NAME));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
        } 
        
        SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(
                new StreamListener(), 
                0, 1000, TimeUnit.MILLISECONDS);
        
        return new Table(DYNAMO_DB, DYNAMO_DB_TABLE_NAME);
    }
    
    private static Properties getProperties(String propFileName) throws IOException {
        Properties props = new Properties();
        try {
            InputStream in = SambaGlobalCache.class.getClassLoader().getResourceAsStream(propFileName);
            if (in != null) {
                props.load(in);
            } 
            props.putAll(System.getProperties());
            return props;
        } catch (IOException e) {
            LOGGER.error("Error occured while loading properties from " + "'" + propFileName + "'", e);
            throw e;
        }
    }

    private class StreamListener implements Runnable {

        private final ConcurrentMap<String, String> shardIteratorMap = 
                new ConcurrentHashMap<String, String>();
        private final AtomicBoolean inProgress = new AtomicBoolean();
        
        private StreamListener() {
            execute(true);
        }
        
        @Override
        public void run() {
            execute(false);
        }
        
        private void execute(boolean initial) {
            if (inProgress.compareAndSet(false, true)) {
                try {
                    DescribeTableResult describeTableResult = 
                            DYNAMO_DB.describeTable(DYNAMO_DB_TABLE_NAME);
                    String tableStreamArn = describeTableResult.getTable().getLatestStreamArn();
                    DescribeStreamResult describeStreamResult = 
                            DYNAMO_DB_STREAMS.describeStream(
                                    new DescribeStreamRequest().withStreamArn(tableStreamArn));
                    String streamArn = describeStreamResult.getStreamDescription().getStreamArn();
                    List<Shard> shards = describeStreamResult.getStreamDescription().getShards();
                    
                    for (Shard shard : shards) {
                        String shardId = shard.getShardId();
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.info("Processing " + shardId + " from stream " + streamArn + " ...");
                        }
                        
                        String shardIterator = shardIteratorMap.get(shardId);
                        if (shardIterator == null) {
                            ShardIteratorType shardIteratorType = ShardIteratorType.LATEST;
                            if (!initial) {
                                shardIteratorType = ShardIteratorType.TRIM_HORIZON;
                            }
                            GetShardIteratorRequest getShardIteratorRequest = 
                                    new GetShardIteratorRequest().
                                            withStreamArn(tableStreamArn).
                                            withShardId(shardId).
                                            withShardIteratorType(shardIteratorType);
                            GetShardIteratorResult shardIteratorResult = 
                                DYNAMO_DB_STREAMS.getShardIterator(getShardIteratorRequest);
                            String newShardIterator = shardIteratorResult.getShardIterator();
                            String oldShardIterator = 
                                    shardIteratorMap.putIfAbsent(shardId, newShardIterator);
                            if (oldShardIterator == null) {
                                shardIterator = newShardIterator;
                            } else {
                                shardIterator = oldShardIterator;
                            }
                        }
                        String nextItr = shardIterator;
                        while (nextItr != null) {
                            GetRecordsResult getRecordsResult = 
                                    DYNAMO_DB_STREAMS.getRecords(
                                            new GetRecordsRequest().withShardIterator(nextItr));
                            List<Record> records = getRecordsResult.getRecords();
                            for (Record record : records) {
                                StreamRecord streamRecord = record.getDynamodb();
                                String eventName = record.getEventName();
                                String key = streamRecord.getKeys().get("id").getS();
                                if ("INSERT".equals(eventName)) {
                                    byte[] newData = streamRecord.getNewImage().get("data").getB().array();
                                    String source = streamRecord.getNewImage().get("source").getS();
                                    Object newValue = newData != null ? deserialize(newData) : null;
                                    if (!source.equals(UUID)) { 
                                        for (CacheChangeListener listener : cacheChangeListeners) {
                                            listener.onInsert(key, newValue);
                                        }
                                    }    
                                } else if ("MODIFY".equals(eventName)) {
                                    byte[] oldData = streamRecord.getOldImage().get("data").getB().array();
                                    byte[] newData = streamRecord.getNewImage().get("data").getB().array();
                                    String source = streamRecord.getNewImage().get("source").getS();
                                    Object oldValue = oldData != null ? deserialize(oldData) : null;
                                    Object newValue = newData != null ? deserialize(newData) : null;
                                    if (!source.equals(UUID)) { 
                                        for (CacheChangeListener listener : cacheChangeListeners) {
                                            listener.onUpdate(key, oldValue, newValue);
                                        }
                                    }    
                                } else if ("REMOVE".equals(eventName)) {
                                    for (CacheChangeListener listener : cacheChangeListeners) {
                                        listener.onDelete(key);
                                    }
                                } else {
                                    LOGGER.warn("Unknown event name: " + eventName);
                                }
                            }
                            shardIteratorMap.put(shardId, nextItr);
                            if (records.isEmpty()) {
                                break;
                            }
                            nextItr = getRecordsResult.getNextShardIterator();
                        }
                    }
                } catch (Throwable t) {
                    LOGGER.error("Error occurred while processing stream events!", t);
                } finally {
                    inProgress.set(false);
                }
            }    
        }
        
    }

    private class ReusableKryo extends Kryo {
        
        private static final int BUFFER_SIZE = 4096;
        
        private final FastOutput output = new FastOutput(BUFFER_SIZE);

        private byte[] encode(Object obj) {
            output.clear();
            writeClassAndObject(output, obj);
            return output.toBytes();
        }
        
        private Object decode(byte[] data) {
            return readClassAndObject(new FastInput(data));
        }
    }
    
    private byte[] serialize(Object obj) {
        return threadLocalKryo.get().encode(obj);
    }
    
    @SuppressWarnings("unchecked")
    private <T> T deserialize(byte[] data) {
        return (T) threadLocalKryo.get().decode(data);
    }
    
    public void registerCacheChangeListener(CacheChangeListener cacheChangeListener) {
        cacheChangeListeners.add(cacheChangeListener);
    }
    
    public void deregisterCacheChangeListener(CacheChangeListener cacheChangeListener) {
        cacheChangeListeners.remove(cacheChangeListener);
    }

    @Override
    public SambaCacheType getType() {
        return SambaCacheType.GLOBAL;
    }
    
    @Override
    public SambaCacheConsistencyModel getConsistencyModel() {
        return SambaCacheConsistencyModel.STRONG_CONSISTENCY;
    }

    @Override
    public <V> V get(String key) {
        V value;
        Item item = 
                DYNAMO_DB_TABLE.getItem(
                        new GetItemSpec().
                                withPrimaryKey("id", key).
                                withConsistentRead(true));
        if (item == null) {
            value = null;
        } else {
            byte[] data = item.getBinary("data");
            if (data == null) {
                value = null;
            } else {
                value = deserialize(data);
            }
        }    
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value %s has been retrieved from global cache with key %s", key, value));
        }
        return value;
    }

    @Override
    public void put(String key, Object value) {
        if (value == null) {
            remove(key);
        } else {
            byte[] data = serialize(value);
            Item item = 
                    new Item().
                        withPrimaryKey("id", key).
                        withBinary("data", data).
                        with("source", UUID);
            DYNAMO_DB_TABLE.putItem(item);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        String.format("Value %s has been put into global cache with key %s", key, value));
            }
        }    
    }
    
    @Override
    public boolean replace(String key, Object oldValue, Object newValue) {
        boolean replaced = false;
        if (oldValue == null && newValue != null) {
            byte[] newData = serialize(newValue);
            Item item = 
                    new Item().
                        withPrimaryKey("id", key).
                        withBinary("data", newData).
                        with("source", UUID);
            try {
                DYNAMO_DB_TABLE.putItem(item, new Expected("id").notExist());
                replaced = true;
            } catch (ConditionalCheckFailedException e) {
            }
        } else if (oldValue != null && newValue == null) {
            byte[] oldData = serialize(oldValue);
            try {
                DYNAMO_DB_TABLE.deleteItem("id", key, new Expected("data").eq(oldData));
                replaced = true;
            } catch (ConditionalCheckFailedException e) {
            }
        } else if (oldValue != null && newValue != null) {
            byte[] oldData = serialize(oldValue);
            byte[] newData = serialize(newValue);
            Item item = 
                    new Item().
                        withPrimaryKey("id", key).
                        withBinary("data", newData).
                        with("source", UUID);
            try {
                DYNAMO_DB_TABLE.putItem(item, new Expected("data").eq(oldData));
                replaced = true;
            } catch (ConditionalCheckFailedException e) {
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
        DYNAMO_DB_TABLE.deleteItem("id", key);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    String.format("Value has been removed from global cache with key %s", key));
        }
    }
    
    @Override
    public void clear() {
        ItemCollection<ScanOutcome> items = DYNAMO_DB_TABLE.scan();
        IteratorSupport<Item, ScanOutcome> itemsIter = items.iterator();
        while (itemsIter.hasNext()) {
            Item item = itemsIter.next();
            DYNAMO_DB_TABLE.deleteItem("id", item.get("id"));
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Global cache has been cleared");
        }
    }

}
