- [免责声明](#免责声明)
- [项目介绍](#项目介绍)
- [参考文档](#参考文档)
  - [application 配置项](#application_配置项)
    - [SafeCacheProperties：缓存击穿相关配置](#SafeCacheProperties：缓存击穿相关配置)
    - [SafeCacheBloomFilterProperties：缓存穿透相关配置](#SafeCacheBloomFilterProperties：缓存穿透相关配置)
    - [Redis 相关配置](#Redis_相关配置)
  - [注解使用](#注解使用)
    - [@CacheStringSearch](#@CacheStringSearch)
    - [@CacheHashSearch](#@CacheHashSearch)
    - [@BloomFilter](#@BloomFilter)
    - [@AddDataToBloomFilter @AddDataToBloomFilters](#@AddDataToBloomFilter/@AddDataToBloomFilters)
  - [Redis key](#Redis_key)
  - [定时任务](#定时任务)
    - [更新定时任务1-布隆过滤器定时更新](#更新定时任务1-布隆过滤器定时更新)
    - [更新定时任务2-误判率检测](#更新定时任务2-误判率检测)
    - [定时任务3-Master服务器租期检查](#定时任务3-Master服务器租期检查)

# 免责声明

**注意：本项目仅用于招聘时的项目展示，不建议用于其他用途。请阅读以下内容，了解项目使用的限制和免责声明。**

这个项目是为了展示我的技能和能力，供招聘时参考。请注意，我并不希望其他人使用或基于此项目进行开发。

- **不建议使用：** 请不要基于本项目进行任何形式的衍生、复制、分发或用途扩展。我不授权任何人使用本项目的代码、资源或思路。

- **不提供支持：** 本项目是在招聘展示的目的下创建，我不承诺为该项目提供任何形式的技术支持、问题解答或维护服务。

- **风险自负：** 如果您选择忽略此警告并使用本项目，您将自行承担可能出现的问题、错误或损失。我不对因使用本项目而导致的任何后果负责。

请尊重我的意愿并遵守这些限制。感谢您的理解和配合。

# 项目介绍

**通过自定义注解+封装SpringBoot starter的方式，使用分布式读写锁解决缓存击穿，使用布隆过滤器解决缓存穿透（包括布隆过滤器的自动加载、查询、更新、http操作接口等）。**

您可以通过查看以下图片或直接查看`项目介绍.pptx`来了解项目的原理及使用方法。

**项目介绍**

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片2.JPG?raw=true" alt="幻灯片2.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片3.JPG?raw=true" alt="幻灯片3.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片4.JPG?raw=true" alt="幻灯片4.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片5.JPG?raw=true" alt="幻灯片5.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片6.JPG?raw=true" alt="幻灯片6.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片7.JPG?raw=true" alt="幻灯片7.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片8.JPG?raw=true" alt="幻灯片8.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片9.JPG?raw=true" alt="幻灯片9.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片10.JPG?raw=true" alt="幻灯片10.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片11.JPG?raw=true" alt="幻灯片11.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片12.JPG?raw=true" alt="幻灯片12.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片13.JPG?raw=true" alt="幻灯片13.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片14.JPG?raw=true" alt="幻灯片14.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片15.JPG?raw=true" alt="幻灯片15.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片16.JPG?raw=true" alt="幻灯片16.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片17.JPG?raw=true" alt="幻灯片17.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片18.JPG?raw=true" alt="幻灯片18.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片19.JPG?raw=true" alt="幻灯片19.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片20.JPG?raw=true" alt="幻灯片20.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片21.JPG?raw=true" alt="幻灯片21.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片22.JPG?raw=true" alt="幻灯片22.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片23.JPG?raw=true" alt="幻灯片23.JPG"  />

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/项目介绍/幻灯片24.JPG?raw=true" alt="幻灯片24.JPG"  />

# 参考文档

## application_配置项

### SafeCacheProperties：缓存击穿相关配置

用于`@CacheStringSearch`、`@CacheHashSearch`；以下配置项均同时存在于`application配置文件` 和 `注解参数`中，如未在`application`中进行配置，则会使用`注解参数`中的值

| 配置项                                  | 解释                 | 数据类型 |
| --------------------------------------- | -------------------- | -------- |
| wfs-safe-cache.timeout                 | 缓存存活时间         | Long     |
| wfs-safe-cache.unit                    | 缓存存活时间单位     | TimeUnit |
| wfs-safe-cache.enable-distributed-lock | 是否使用分布式锁     | Boolean  |
| wfs-safe-cache.save-null               | 是否缓存null         | Boolean  |
| wfs-safe-cache.timeout-of-null         | null缓存存活时间     | Long     |
| wfs-safe-cache.unit-of-null            | null缓存存活时间单位 | TimeUnit |

### SafeCacheBloomFilterProperties：缓存穿透相关配置

用于布隆过滤器初始化、定时任务、`@BloomFIlter`中`updateCron`字段、http操作`permissionPassword`的相关配置

| 配置项                                                   | 解释                                                         | 数据类型 |
| -------------------------------------------------------- | ------------------------------------------------------------ | -------- |
| wfs-safe-cache.bloom-filter.update-cron                 | 布隆过滤器定时更新任务的cron表达式，用于所有布隆过滤器的统一配置，若需要停用定时更新，可设置为`stop`<br />该配置项同时存在于`application配置文件` 和 `注解参数`中，如未在`application`中进行配置，则会使用`注解参数`中的值 | String   |
| wfs-safe-cache.bloom-filter.reset-update-cron           | 项目启动时是否重置redis中已有的BloomFilter:UpdateCron**（default=true）** | boolean  |
| wfs-safe-cache.bloom-filter.reset-bloom-filter-config   | 项目启动时是否重置 redis 中已有的布隆过滤器的配置信息（具体指 `InitOfExpectedInsertions`和`InitOfFalseProbability`）**（default=true）**<br />如果设置为`true`，则项目启动时会应用`@BloomFIlter`参数中的`InitOfExpectedInsertions`和`InitOfFalseProbability`作为布隆过滤器的创建参数，该参数会作为布隆过滤器的配置信息存入redis中。<br />如果设置为`false`，则会优先使用redis中已有的布隆过滤器的`InitOfExpectedInsertions`和`InitOfFalseProbability`作为布隆过滤器的创建参数；如果redis中不存在对应参数则会使用`@BloomFIlter`中的参数。<br />如果你对线上的布隆过滤器进行了修改，并希望这些修改可以在下次服务器启动时保留，而不是手动在对应的`@BloomFIlter`中进行参数修改，则可以将该配置项设为`false` | boolean  |
| wfs-safe-cache.bloom-filter.max-amount-of-inserted-data | 布隆过滤器初始化时，每批次从数据库获取的最大数据量，即分页查询数据库中数据，将数据插入进布隆过滤器时，每页获取的数据条数（**default=1000**，每批次最多获取1000条数据）<br />当数据库数据量过大，不适宜单次将数据库数据全部获取到服务器中 | int      |
| wfs-safe-cache.bloom-filter.max-amount-of-inserted-data | 是否开启驼峰转下划线映射**（default=true）**                 | boolean  |
| wfs-safe-cache.bloom-filter.false-rate-monitor-cron     | 监视误判率的定时任务的Cron表达式，如果布隆过滤器的Redis中数据遭到非法删除或修改，也会触发更新（**default="0 5/10 * * * ?"**，从每小时的五分开始，每十分钟检查所有布隆过滤器的误判率） | String   |
| wfs-safe-cache.bloom-filter.queries-threshold           | 误判率监视器的Queries阈值，即查询次数大于queriesThreshold且误判率大于falseRateThreshold时进行布隆过滤器更新**（default=100000）** | int      |
| wfs-safe-cache.bloom-filter.false-rate-threshold        | 误判率监视器的FalseRate阈值，即查询次数大于queriesThreshold且误判率大于falseRateThreshold时进行布隆过滤器更新**（default=0.01）** | double   |
| wfs-safe-cache.bloom-filter.check-lease-cron            | 检查Master服务器租期的定时任务（**default="0 0/5 * * * ?"**，每五分钟检查一次Master服务器租期） | String   |
| wfs-safe-cache.bloom-filter.lease-term                  | 单次租期时长**（default=9）**                                | long     |
| wfs-safe-cache.bloom-filter.lease-term-time-unit        | 单次租期时长的时间单位                                       | TimeUnit |
| wfs-safe-cache.bloom-filter.permission-password         | 进行布隆过滤器http操作的权限识别码**（default="root"）**     | String   |

### Redis_相关配置

| 配置项                               | 解释                                                         | 数据类型 |
| ------------------------------------ | ------------------------------------------------------------ | -------- |
| wfs-safe-cache.redis.unified-config | 是否使用统一配置（是否使用spring.redis中的配置）**（default=true）**<br />如果设置为true，则使用spring.redis中的配置<br />如果设置为false，则使用单独的redis配置，你可以将该jar包的缓存数据存放于与spring项目本体不同的redis中；仅支持Lettuce，其余配置项与spring.redis相同 | boolean  |
| wfs-safe-cache.redis.***            | 当wfs-safe-cache.redis.unified-config=false时启用，仅支持Lettuce，其余配置项与spring.redis配置项相同 |          |

## 注解使用

### @CacheStringSearch

防止缓存击穿，用于操作 redis string类型

注解参数

| 参数名称                      | 解释                                                         | 默认值           |
| ----------------------------- | ------------------------------------------------------------ | ---------------- |
| String businessName           | 业务名称                                                     | ""，空字符串     |
| String cacheKey               | 缓存查询的key，仅支持spel语句                                |                  |
| boolean globalConfig          | 是否使用application配置文件中的配置<br />设置为true，则优先使用application文件中的全局配置；全局配置中为null的使用注解参数的配置<br />设置为false，使用注解参数的配置 | true             |
| long timeout                  | 缓存存活时间                                                 | -1L              |
| TimeUnit unit                 | 缓存存活时间单位                                             | TimeUnit.SECONDS |
| boolean enableDistributedLock | 是否使用分布式锁<br />设置为true，则使用Redisson的分布式锁（非公平锁）<br />设置为false，则使用java提供的本地锁（公平锁） | false            |
| boolean saveNull              | 查询到数据库中数据为null时，是否存储null                     | false            |
| long timeoutOfNull            | null缓存存活时间                                             | 30               |
| TimeUnit unitOfNull           | null缓存存活时间单位                                         | TimeUnit.SECONDS |

使用案例：

```java
@CacheStringSearch(businessName = "searchByNameAndAge", cacheKey = "'person:'+#name+#age")
public List<Person> searchByNameAndAge(String name, int age) {
    QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("name", name).eq("age", age);
    List<Person> people = personMapper.selectList(queryWrapper);
    return people;
}

	or

@CacheStringSearch(businessName = "searchByNameAndAge", 
                   cacheKey = "'person:'+#name+#age", 
                   globalConfig = false, 
                   timeout = 30, 
                   unit = TimeUnit.MILLISECONDS, 
                   enableDistributedLock = true, 
                   saveNull = true, 
                   timeoutOfNull = 30, 
                   unitOfNull = TimeUnit.SECONDS)
public List<Person> searchByNameAndAge(String name, int age) {
    QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("name", name).eq("age", age);
    List<Person> people = personMapper.selectList(queryWrapper);
    return people;
}
```

### @CacheHashSearch

防止缓存击穿，用于操作 redis hash类型

注解参数，不支持存活时间的配置

| 参数名称                      | 解释                                                         | 默认值       |
| ----------------------------- | ------------------------------------------------------------ | ------------ |
| String businessName           | 业务名称                                                     | ""，空字符串 |
| String cacheKey               | 缓存查询的key（大key），仅支持spel语句                       |              |
| String hashKey                | 缓存查询的哈希表中的key（小key），仅支持spel语句             |              |
| boolean globalConfig          | 是否使用application配置文件中的配置<br />设置为true，则优先使用application文件中的全局配置；全局配置中为null的使用注解参数的配置<br />设置为false，使用注解参数的配置 | true         |
| boolean enableDistributedLock | 是否使用分布式锁<br />设置为true，则使用Redisson的分布式锁（非公平锁）<br />设置为false，则使用java提供的本地锁（公平锁） | false        |
| boolean saveNull              | 查询到数据库中数据为null时，是否存储null                     | false        |

使用案例：

```java
@CacheHashSearch(businessName = "searchByNameAndAge", cacheKey = "'person'", hashKey = "#name+#age")
public List<Person> searchByNameAndAge(String name, int age) {
    QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("name", name).eq("age", age);
    List<Person> people = personMapper.selectList(queryWrapper);
    return people;
}
```

**@CacheStringSearch、@CacheHashSearch  原理：**

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/图片1.png?raw=true" alt="图片1.png" style="zoom:50%;" />

***

### @BloomFilter

用于布隆过滤器的注册、初始化 和 数据查询过滤

注解参数

| 参数名称                        | 解释                                                         | 默认值                                 |
| ------------------------------- | ------------------------------------------------------------ | -------------------------------------- |
| String businessName             | 业务名称                                                     | ""，空字符串                           |
| String[] bloomFilterName        | 布隆过滤器的名称<br />可指定多个，即将多个布隆过滤器绑定到一个查询方法上。可应用于多字段灵活匹配、多表查询等<br />指定多个时，需设定匹配模式：为单匹配模式，一个布隆过滤器判断有数据就可以放行；为全匹配，所有布隆过滤器判断有数据才可以放行。 |                                        |
| String[] dataOfBloomFilter      | 在布隆过滤器中进行搜索的数据，仅支持spel语句<br />实际搜索的数据需要与fieldName字段一致，例如：当fieldName = "name+age"时，dataOfBloomFilter需要根据方法传入的参数不同，将dataOfBloomFilter指定为：dataOfBloomFilter = "#name+#age" 或 dataOfBloomFilter = "#person.name+#person.age" <br />可指定多个，与bloomFilterName一一对应 |                                        |
| BloomFilterEnum pattern         | 匹配模式，当指定多个布隆过滤器时启用                         | BloomFilterEnum.FULL_MATCH，全匹配模式 |
| long[] InitOfExpectedInsertions | 布隆过滤器的预期数据插入量，该参数为用于布隆过滤器创建的两个参数之一<br />可指定多个，与bloomFilterName一一对应 |                                        |
| double[] InitOfFalseProbability | 布隆过滤器的预期错误率，该参数为用于布隆过滤器创建的两个参数之一<br />可指定多个，与bloomFilterName一一对应 |                                        |
| Class<?>[] InitOfEntity         | 布隆过滤器对应实体类，需要标注有@TableName；标注有@TableField的字段，会根据@TableField进行SQL与字段进行映射，没有@TableField的字段默认 驼峰命名 转 下划线命，可在配置文件中进行配置<br />应用于布隆过滤器初始化阶段，用于操作数据库插入数据<br />可指定多个，与bloomFilterName一一对应 |                                        |
| String[] InitOfFieldName        | 向布隆过滤器中插入的Entity的字段（数据库表的列名），多个字段用+号分隔，格式：fieldName = "name" 或 fieldName = "name+age"；实际插入进布隆过滤器的数据为 "张三" 或 "张三30" ，应用于布隆过滤器初始化阶段<br />可指定多个，与bloomFilterName一一对应 |                                        |
| String updateCron               | 定时更新任务的cron，可以在application文件中进行全局配置<br />注意：如果配置文件中设置 wfs-safe-cache.bloom-filter.reset-update-cron = false，则使用 Redis 中存储的 BloomFilter:UpdateCron | "0 0 3 * * ?"                          |
| boolean globalConfigCron        | 是否使用配置文件中的 updateCron 配置（设置为true，则优先使用全局配置中的wfs-safe-cache.bloom-filter.update-cron；配置文件中 wfs-safe-cache.bloom-filter.update-cron 为 null 则使用注解参数配置）<br />注意：如果配置文件中设置 wfs-safe-cache.bloom-filter.reset-update-cron = false，则使用 Redis 中存储的 BloomFilter:UpdateCron | true                                   |

使用案例：

```java
//单布隆过滤器
@BloomFilter(businessName = "searchByNameAndAge", 
             bloomFilterName = "searchByNameAndAgeBloomFilter",
             dataOfBloomFilter = "#name+#age", 
             InitOfExpectedInsertions = 10000, 
             InitOfFalseProbability = 0.01, 
             InitOfEntity = Person.class, 
             InitOfFieldName = "name+age", 
             updateCron = "0 0 4 * * ?", 
             globalConfigCron = false)
public List<Person> searchByNameAndAge(String name, int age) {
    QueryWrapper<Person> queryWrapper = new QueryWrapper<>();
    queryWrapper.eq("name", name).eq("age", age);
    List<Person> people = personMapper.selectList(queryWrapper);
    return people;
}

//多布隆过滤器
@BloomFilter(businessName = "searchByNameAndAge", 
             bloomFilterName = {"BloomFilter1", "BloomFilter2"},
             dataOfBloomFilter = {"#name+#age", "#name+#age"},
             InitOfExpectedInsertions = {10000, 20000},
             InitOfFalseProbability = {0.01, 0.05},
             InitOfEntity = {Person1.class, Person2.class},
             InitOfFieldName = {"name+age", "name+age"}, 
             pattern = BloomFilterEnum.SINGLE_MATCH)
public List<Person> searchByNameAndAge(String name, int age) {
	...
}
```

***

1. 项目启动

   <img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/图片2.png?raw=true" alt="图片2.png" style="zoom:50%;" />

   - 项目启动时会进行参数校验，包括：注解参数中数组的数量是否不一致，不一致抛异常；判断bloomFilterName是否唯一，不唯一抛异常；cron表达式是否符合规范，不规范抛异常
   - 项目启动时会在redis中尝试向`key=BloomFilter:MasterServer`中存入当前服务器的域名、端口（`redisTemplate.opsForValue().setIfAbsent`），如果存放成功，则该服务器为Master服务器，并由该服务器对布隆过滤器进行初始化和定时任务的注册。（详见定时任务3）
     - 如果服务抛异常导致服务关闭，或服务主动关闭，会自动删除`key=BloomFilter:MasterServer`中的Master服务器信息。如服务因断电等原因直接关闭，`key=BloomFilter:MasterServer`会在存活时间到期后由redis删除，并由其余存活的服务占有该key成为新的Master服务器。
   - 项目启动时会开启检查租期定时任务（定时任务3）
   - 布隆过滤器初始化时，如果该布隆过滤器已经存在于Redis中，则会删除重建。
   - 布隆过滤器初始化时，会在redis中存放统计信息，包括该布隆过滤器的查询次数（`key=BloomFilter:bloomFilterName:NumberOfQueries`，value=0）和误判次数（`key=BloomFilter:bloomFilterName:NumberOfFalse`，value=0）
   - 布隆过滤器初始化时，会添加该布隆过滤器的定时更新任务（定时任务1）
   - 布隆过滤器全部初始化成功后，会添加误判率监视器定时任务（定时任务2）
   - 布隆过滤器初始化过程中，因抛异常导致服务停止前，会删除初始化失败的布隆过滤器。如果删除失败，或因断电等原因导致异常处理代码没有运行，请手动删除redis中初始化失败的布隆过滤器，或等待其余服务器成为Master服务器并重新初始化布隆过滤器。

2. 查询流程
   <img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/图片3.png?raw=true" alt="图片3.png" style="zoom:50%;" />

   布隆过滤器正在被占用：正在更新 或 正在删除

   每次查询，`key=BloomFilter:bloomFilterName:NumberOfQueries`会+1；如果查询数据库，数据为null，说明发生误判，`key=BloomFilter:bloomFilterName:NumberOfFalse`会+1

### @AddDataToBloomFilter/@AddDataToBloomFilters

向布隆过滤器中添加数据，保证在MySQL中数据的插入、更新方法上添加该注解。

注解参数

| 参数名称                 | 解释                                   | 默认值       |
| ------------------------ | -------------------------------------- | ------------ |
| String businessName      | 业务名称                               | ""，空字符串 |
| String bloomFilterName   | 布隆过滤器名称                         |              |
| String dataOfBloomFilter | 插入进布隆过滤器中数据，仅支持spel语句 |              |

使用案例：

```java
@AddDataToBloomFilter(businessName = "insertPerson", 
                      bloomFilterName = "searchByName", 
                      dataOfBloomFilter = "#name")
@AddDataToBloomFilter(businessName = "insertPerson", 
                      bloomFilterName = "searchByNameAndAgeBloomFilter", 
                      dataOfBloomFilter = "#name+#age")
public void insertPerson(String name, int age, String message) {
    Person person = new Person();
    person.setName(name);
    person.setAge(age);
    person.setMessage(message);
    personMapper.insert(person);
}

or

@AddDataToBloomFilters({
            @AddDataToBloomFilter(businessName = "insertPerson", 
                                  bloomFilterName = "searchByName", 
                                  dataOfBloomFilter = "#name"),
            @AddDataToBloomFilter(businessName = "insertPerson", 
                                  bloomFilterName = "searchByNameAndAgeBloomFilter", 
                                  dataOfBloomFilter = "#name+#age")})
public void insertPerson(String name, int age, String message) {
    Person person = new Person();
    person.setName(name);
    person.setAge(age);
    person.setMessage(message);
    personMapper.insert(person);
}
```

**原理：**

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/图片4.png?raw=true" alt="图片4.png" style="zoom:50%;" />

## Redis_key

布隆过滤器：key=BloomFilter:bloomFilterName

Master服务器信息：key=BloomFilter:MasterServer

布隆过滤器更新cron：key=BloomFilter:UpdateCron （hash类型）

误判率监控器cron：key=BloomFilter:FalseRateMonitorCron

布隆过滤器分布式锁：BloomFilter:Lock:bloomFilterName

查询次数：BloomFilter:bloomFilterName:NumberOfQueries

误判次数：BloomFilter:bloomFilterName:NumberOfFalse

## 定时任务

由于布隆过滤器中不允许有删除操作，随着使用时间的增长，误判率会逐渐增长。

1. 数据量不断增长，大幅超过布隆过滤器初始的数据量设置，导致布隆过滤器数组中大部分值都为1。
2. 数据大量更新/删除，但布隆过滤器任认为旧数据存在。

误判：布隆过滤器认为数据存在，但数据库中数据不存在。出现误判不会影响查询的结果。
不会出现：布隆过滤器认为数据不存在，但数据库中数据存在。

为解决“误判率会逐渐增长”的状况，添加定时任务用于定时更新布隆过滤器。

### 更新定时任务1-布隆过滤器定时更新

项目启动时注册进Master服务器，仅有Master服务器拥有更新、删除定时任务1的权限（调用http接口的权限）

每个布隆过滤器可设置不同的cron

对应cron配置：

1. application文件全局配置--wfs-safe-cache.bloom-filter.update-cron
2. 注解参数单独配置--@BloomFilter  String updateCron，默认="0 0 3 * * ?"

如果布隆过滤器已经被删除，则不会被更新，如需更新需手动调用http接口

### 更新定时任务2-误判率检测

项目启动时注册进Master服务器，仅有Master服务器拥有更新、删除定时任务2的权限（调用http接口的权限）

cron配置：application文件全局配置--wfs-safe-cache.bloom-filter.false-rate-monitor-cron，默认="0 5/10 * * * ?"

对所有布隆过滤器进行扫描，将 **查询次数大于阈值 **且 **误判率大于阈值** 的布隆过滤器进行更新。

查询次数阈值：`BloomFilter:bloomFilterName:NumberOfQueries` > `wfs-safe-cache.bloom-filter.queries-threshold`

误判率阈值：(`BloomFilter:bloomFilterName:NumberOfFalse`/`BloomFilter:bloomFilterName:NumberOfQueries`) > `wfs-safe-cache.bloom-filter.false-rate-threshold`

如果布隆过滤器已经被删除，则不会被更新，如需更新需手动调用http接口

### 定时任务3-Master服务器租期检查

对于分布式项目，多台服务器使用同一个布隆过滤器进行查询，但只有一台服务器用于实现布隆过滤器的初始化和定时更新。（如果多台服务器都进行初始化或定时更新操作，则会出现冲突；且定时任务只能注册进本地，只能由本服务器进行定时任务的取消等操作）

拥有布隆过滤器初始化，定时任务的更改、删除权限的服务器称为主服务器。

当主服务器出现意外挂机时，布隆过滤器定时更新任务无法进行。为此，创建检查主服务器租期定时任务。

<img src="https://github.com/wfs674895019/PersonalProjecPresentation/blob/master/README-img/图片5.png?raw=true" alt="图片5.png" style="zoom:50%;" />

1. 对于主服务器：每wfs-safe-cache.bloom-filter.check-lease-cron续租BloomFilter:MasterServer一次，租期wfs-safe-cache.bloom-filter.lease-term分钟
2. 对于其他服务器：每wfs-safe-cache.bloom-filter.check-lease-cron检查一次BloomFilter:MasterServer，如果BloomFilter:MasterServer不存在，说明主服务器已挂机，该服务器抢夺BloomFilter:MasterServer，并重新初始化布隆过滤器、注册定时更新任务。

wfs-safe-cache.bloom-filter.check-lease-cron，default="0 0/5 * * * ?"
wfs-safe-cache.bloom-filter.lease-term，default=9