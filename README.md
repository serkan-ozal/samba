1. What is Samba?
==============
In general **Samba** is a very simple caching library with very basit caching functionalities (get, put, replace, remove) to be used as local, global or tiered (local + global). 

**Samba** is designed for non-blocking cache access with lock-free algorithms from stratch. Therefore, being high-performant is one of the its major requirements. In addition, keeping its strong/eventual consistency model promise its another major requirement.

Eventhough **Samba** can be useful for many cases as simple caching layer, at first, it is aimed to be used at AWS's **Lambda** service for sharing state/information between different Lambda function invocations whether on the same container (process) or another container (process/machine). See [here](https://aws.amazon.com/blogs/compute/container-reuse-in-lambda/) and [here](https://www.linkedin.com/pulse/aws-lambda-container-lifetime-config-refresh-frederik-willaert) for more details.

Here are the [demos](https://github.com/serkan-ozal/samba-aws-lambda-demo).

2. Installation
==============
In your `pom.xml`, you must add repository and dependency for **Samba**. 
You can change `samba.version` to any existing **Samba** library version.
Latest version of **Samba** is `1.0-SNAPSHOT`.

``` xml
...
<properties>
    ...
    <samba.version>1.0-SNAPSHOT</samba.version>
    ...
</properties>
...
<dependencies>
    ...
	<dependency>
		<groupId>tr.com.serkanozal</groupId>
		<artifactId>samba</artifactId>
		<version>${samba.version}</version>
	</dependency>
	...
</dependencies>
...
<repositories>
	...
	<repository>
		<id>serkanozal-maven-repository</id>
		<url>https://github.com/serkan-ozal/maven-repository/raw/master/</url>
	</repository>
	...
</repositories>
...
```

3. Configuration
==============

3.1. AWS Credentials
--------------
* **`aws.accessKey:`** Your AWS access key
* **`aws.secretKey:`** Your AWS secret key

These properties can be specified as system property or can be given from **`aws-credentials.properties`** configuration file.

3.2. Samba Configurations
--------------
* **`cache.global.tableName:`** Configures name of the table on AWS's **DynamoDB** to store cache entries as global cache. Default value is `___SambaGlobalCache___`.
* **`cache.global.readCapacityPerSecond:`** Configures expected maxiumum read capacity to provision required throughput from AWS's **DynamoDB**. Default value is `1000`.
* **`cache.global.writeCapacityPerSecond:`** Configures expected maxiumum write capacity to provision required throughput from AWS's **DynamoDB**. Default value is `100`.

4. Usage
==============
The contact point for the user is `SambaField`. There is one-to-one relationship between the `SambaField` instance and the value/property that you want to access/share statefully. 

There are three types of cache to be used as backend of `SambaField`:
* `LOCAL`: Keeps cache entries in local memory. Under the hood, uses Cliff Click's **high-scale-lib** for lock-free and high-performance accesses. In this mode, `SambaField` instance supports **strong consistency** model. If you want to store live (may not be right term???) objects such as database connections, this mode is suggested. Because, in this mode, objects are not serialized/deserialized and when you get the stored object, you get the same object instance with the stored object instance.
* `GLOBAL`: Keeps cache entries at remote storage. Under the hood, uses AWS's **DynamoDB** for highly-scalable and high-performance accesses. In this mode, `SambaField` instance supports **strong consistency** model. This mode is not meaningful to store live (may not be right term???) objects such as database connections. Because in this mode, objects are serialized/deserialized and when you get the stored object, you get different object instance with the stored object instance.
* `TIERED`: Keeps caches on both of local and remote storages. While setting/clearing field value, value is set/cleared on both of local and global caches. In addition, while getting field value, at first it is looked up on local cache. If it is available and not invalidated, it is directly retrieved from local cache, otherwise it is requested from remote global cache. In this mode, `SambaField` instance supports **eventual consistency** model. This means that if an entry is updated or removed from global cache by someone, local cache is evicted and the new value will be retrieved eventually. In this context, there is **monotonic read consistency** but no **linearizability**. See [here](https://en.wikipedia.org/wiki/Consistency_model) and [here](https://aphyr.com/posts/313-strong-consistency-models) for more details. This mode is not meaningful like `GLOBAL` mode to store live (may not be right term???) objects such as database connections because of the same reason about serializing/deserializing stored instances to remote global cache.

``` java
SambaField myLocalCacheBackedField = new SambaField("myLocalCacheBackedField", SambaCacheType.LOCAL);
SambaField myGlobalCacheBackedField = new SambaField("myGlobalCacheBackedField", SambaCacheType.GLOBAL);
SambaField myTieredCacheBackedField = new SambaField("myTieredCacheBackedField", SambaCacheType.TIERED);
```

In addition, you don't need to specify id for the `SambaField` field. If it is not specified, it is automatically generated by using instance creation location (`<class-name, method-name, line-number>`). This means that, the whenever a `SambaField` instance is created at the same location (`<class-name, method-name, line-number>`), all of the instances represent and share the same value/state.

``` java
SambaField myLocalCacheBackedField = new SambaField(SambaCacheType.LOCAL);
SambaField myGlobalCacheBackedField = new SambaField(SambaCacheType.GLOBAL);
SambaField myTieredCacheBackedField = new SambaField(SambaCacheType.TIERED);
```

There are three basic functionalities over `SambaField` field:
* **Get:** Gets the shared state/value of the field. The functionality is invoked via `get()` call over `SambaField` field.
* **Get-or-Create:** Gets the shared state/value of the field if it is exist, otherwise creates new one through given `SambaValueFactory::create()` and sets it atomically if and only if current value is not exist. If setting ncreated value (created via `SambaValueFactory::create()`) fails due to already existing value (at first value is not exist but in the meantime while new instance is being created, another value is set concurrently), existing value is returned and locally created value is destroyed via `SambaValueFactory::destroy(V value)`. The functionality is invoked via `getOrCreate(SambaValueFactory<V> factory)` call over `SambaField` field.
* **Refresh:** Gets the fresh shared state/value of the field. This functionality is used for ensuring **strong consistency** while reading. For **strong consistent** caches (`LOCAL` and `GLOBAL`), refresh functionality is equal get functionality, but for **eventually consistent** caches (`TIERED`), it means consistent read by retrieving data from `GLOBAL` cache by bypassing `LOCAL` cache. The functionality is invoked via `refresh()` call over `SambaField` field.
* **Set:** Sets the shared state/value of the field. The functionality is invoked via `set(V value)` call over `SambaField` field.
* **Compare-and-Set:** Compares and sets the shared state/value of the field atomically if and only if the current field value is equal to given old value. If replacement has succeeded, returns `true`, otherwise `false`. The functionality is invoked over `SambaField` field via `compareAndSet(V oldValue, V newValue)` if old value is specified explicitly or via `compareAndSet(V newValue)` if current value is assumed to be used as old value.
* **Clear:** Clears the shared state/value of the field. The functionality is invoked via `clear()` call over `SambaField` field.
* **Process:** `SambaFieldProcessor` instance takes current value of the field and after some process logic returns new value for the field. Then this returned value is set to field. Note that this is not atomic operation so multiple processors on the same field might override themselves. The functionality is invoked via `process(SambaFieldProcessor processor)` call over `SambaField` field.
* **Process Atomically:** For this atomic version of the process functionality, the new value (output of processor) is set if and only if current value is the same with the value passed into processor. If setting new value succeeds, call returns. Otherwise processor is called multiple times with fresh values of field until it succeeds. The functionality is invoked via `processAtomically(SambaFieldProcessor processor)` call over `SambaField` field.

``` java
// Assume that we are using caches (LOCAL or GLOBAL but not TIERED) 
// which have strong-consistency model.
// Otherwise, some assertions in the following code might fail 
// while verifying them immediately but not eventually.

SambaField<String> myField = ...
...
myField.set("Value-1");
value = myField.get(); // value is "Value-1"
...
myField.set("Value-2");
value = myField.get(); // value is "Value-2" now
...
replaced = myField.compareAndSet("Value-1", "Value-3"); // replaced is false
value = myField.get(); // value is still "Value-2" because compare-and-set has failed
...
replaced = myField.compareAndSet("Value-2", "Value-3"); // replaced is true
value = myField.get(); // value is "Value-3" anymore because compare-and-set has succeeded
...
myField.clear();
value = myField.get(); // value is null
```

5. Benchmark
==============
At low mutation rate (mutate per second), **Samba** was able to achieved **ONE BILLION** !!! (note that not one million) get throughput per second with **strong** (for `LOCAL` cache) or **eventual** (for `TIERED` cache) consistency models on my machine with its **3-level** (field <-> local <-> remote) field caching infrastructure. 

You might try your [own](https://github.com/serkan-ozal/samba/blob/master/src/test/java/tr/com/serkanozal/samba/SambaFieldBenchmark.java)

6. Roadmap
==============
* Ability to intercept specified (via programmatic and/or declarative configuration) field accesses at bytecode level and handle them through `SambaField` automatically.
