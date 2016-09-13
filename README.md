1. What is Samba?
==============
In general **Samba** is a very simple caching library with very basit caching functionalities (get, put, remove) to be used as local, global or tiered (local + global).
* **Local cache:** Keeps cache entries in local memory. Under the hood, uses Cliff Click's **high-scale-lib** for lock-free and high-performance accesses. In this mode, **Samba** supports **strong consistency model**.
* **Global cache:** Keeps cache entries at remote storage. Under the hood, uses AWS's **DynamoDB** for highly-scalable abd high-performance accesses. In this mode, **Samba** supports **strong consistency model**.
* **Tiered cache:** Keeps caches on both of local and remote storages. In this mode, **Samba** supports **eventual consistency model**. This means that if an entry is updated or removed from global cache by someone, local cache is evicted eventually. In this context, there is **monotonic read consistency** but no **linearizability**. See [here](https://en.wikipedia.org/wiki/Consistency_model) and [here](https://aphyr.com/posts/313-strong-consistency-models) for more details.

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

5. Roadmap
==============

