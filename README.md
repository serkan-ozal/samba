1. What is Samba?
==============
In general **Samba** is a very simple caching library with very basit caching functionalities (get, put, remove) to be used as local, global or tiered (local + global). 

**Samba** is designed for non-blocking cache access with lock-free algorithms from stratch. Therefore, being high-performant is one of the its major requirements. In addition, keeping its strong/eventual consistency model promise its another major requirement.

Eventhough **Samba** can be useful for many cases as simple caching layer, at first, it is aimed to be used at AWS's **Lambda** service for sharing state/information between different Lambda function invocations whether on the same container (process) or another container (process/machine). See [here](https://aws.amazon.com/blogs/compute/container-reuse-in-lambda/) and [here](https://www.linkedin.com/pulse/aws-lambda-container-lifetime-config-refresh-frederik-willaert) for more details.


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
* **Get:** Gets the shared state/value of the field. Invoked via `get()` call over `SambaField` field.
* **Set:** Sets the shared state/value of the field. Invoked via `set(T value)` call over `SambaField` field.
* **Clear:** Clears the shared state/value of the field. Invoked via `clear()` call over `SambaField` field.

``` java
SambaField<String> myField = ...
...
myField.set("Value-1");
String value1 = myField.get(); // value1 is "Value-1"
...
myField.set("Value-2");
String value2 = myField.get(); // value2 is "Value-2"
...
myField.clear();
String value3 = myField.get(); // value3 is null
```

5. Roadmap
==============
* Ability to intercept specified (via programmatic and/or declarative configuration) field accesses at bytecode level and handle them through `SambaField` automatically.
