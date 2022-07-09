RocketMQ是一款阿里巴巴开源的消息中间件，在2017年9月份成为Apache的顶级项目，是国内首个互联网中间件在 Apache 上的顶级项目。

RocketMQ的起源受到另一款消息中间件Kafka的启发。最初，淘宝内部的交易系统使用了淘宝自主研发的Notify消息中间件，使用Mysql作为消息存储媒介，可完全水平扩容。为了进一步降低成本和提升写入性能，需要在存储部分可以进一步优化，2011年初，Linkin开源了Kafka这个优秀的消息中间件，淘宝中间件团队在对Kafka做过充分Review之后，被Kafka无限消息堆积，高效的持久化速度所吸引。

不过当时Kafka主要定位于日志传输，对于使用在淘宝交易、订单、充值等场景下还有诸多特性不满足，例如：**延迟消息**，**消费重试**，**事务消息**，**消息过滤**等，这些都是一些企业级消息中间件需要具备的功能。为此，淘宝中间件团队重新用Java语言编写了RocketMQ，定位于非日志的可靠消息传输。不过随着RocketMQ的演进，现在也支持了日志流式处理。

目前RocketMQ经过了阿里多年双十一大促的考验，性能和稳定性得到了充分的严重。目前在业界被广泛应用在订单，交易，充值，流计算，消息推送，日志流式处理，binlog分发等场景。

## 基本概念

- `消息（Message）`： 指消息系统所传输信息的物理载体，生产和消费数据的最小单位，每条消息必须属于一个主题。
- `主题（Topic）`：Topic表示一类消息的集合，每个主题包含若干条消息，每条消息只能属于一个主题，是RocketMQ进行消息订阅的基本单位。 一个生产者可以同时发送多种Topic的消息；而一个消费者只能消费一种Topic的消息。
- `标签（Tag）`：为消息设置的标签，用于同一主题下区分不同类型的消息，可以根据不同业务类型在同一主题下设置不同标签。 Topic是消息的一级分类，Tag是消息的二级分类。
- `队列（Queue）`:一个Topic中可以包含多个Queue，每个Queue中存放的就是该Topic的消息。 一个Topic的Queue中的消息只能被一个消费者组中的一个消费者消费，不能被消费多次。
- `消息标识（MessageId/Key）`: RocketMQ中每个消息拥有唯一的MessageId，且可以携带具有业务标识的Key，以方便对消息的查询。不过需要注意的是，MessageId有两个：在生产者send()消息时会自动生成一个MessageId（msgId)，当消息到达Broker后，Broker也会自动生成一个MessageId(offsetMsgId)。msgId、offsetMsgId与key都称为消息标识，可用于**消息检索**。

- - **msgId**： 由producer端生成，其生成规则为：producerIp + 进程pid +hashCode +当前时间 + AutomicInteger自增计数器
  - **offsetMsgId**： 由broker端生成，其生成规则为：brokerIp + 物理分区的offset（Queue中的偏移量）
  - **key**： 由用户指定的业务相关的唯一标识。

## 技术架构

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657371254694-1f06859a-00b3-4c98-9354-e1d372c343d1.png)

RocketMQ架构上主要分为四部分，如上图所示:

- `Producer`：消息发布的角色，支持分布式集群方式部署。Producer通过MQ的负载均衡模块选择相应的Broker集群队列进行消息投递，投递的过程支持快速失败并且低延迟。
- `Consumer`：消息消费的角色，支持分布式集群方式部署。支持以push推，pull拉两种模式对消息进行消费。同时也支持集群方式和广播方式的消费，它提供实时消息订阅机制，可以满足大多数用户的需求。
- `NameServer`：NameServer是一个Broker与Topic路由的注册中心，支持Broker的动态注册与发现。

- - **Broker管理**： 接受Broker集群的注册信息，提供心跳检测机制，检查Broker是否还存活。
  - **路由信息管理**： 每个NameServer中都保存着Broker集群的整个路由信息和用于客户端查询的队列信息。Producer和Conumser通过NameServer可以获取整个Broker集群的路由信息，从而进行消息的投递和消费。
  - **路由注册**： 在Broker节点启动时，轮询NameServer列表，与每个NameServer节点建立长连接，发起注册请求。对于Broker，必须明确指出所有NameServer地址，因此NameServer并不能随便扩容。因此，若Broker不重新配置，新增的NameServer对于Broker来说是不可见的，其不会向这个NameServer进行 注册。Broker节点为了证明自己是活着的，为了维护与NameServer间的长连接，会将最新的信息以心跳包的方式上报给NameServer，每30秒发送一次心跳。心跳包中包含 BrokerId、Broker地址(IP+Port)、Broker名称、Broker所属集群名称等等
  - **路由剔除**： NameServer中有⼀个定时任务，每隔10秒就会扫描⼀次Broker表，查看每一个Broker的最新心跳时间戳距离当前时间是否超过120秒，如果超过，则会判定Broker失效，然后将其从Broker列表中剔除。
  - **路由发现**： RocketMQ的路由发现采用的是Pull模型。当Topic路由信息出现变化时，NameServer不会主动推送给客户端，而是客户端定时拉取主题最新的路由。默认**客户端(是Producer与Consumer)**每30秒会拉取一次最新的路由。

- `BrokerServer`：Broker主要负责消息的存储、投递和查询以及服务高可用保证，为了实现这些功能，Broker包含了以下几个重要子模块。

- - **Remoting Module**：整个Broker的实体，负责处理来自Client端的请求。
  - **Client Manager**：负责管理客户端(Producer/Consumer)和维护Consumer的Topic订阅信息。
  - **Store Service**：提供方便简单的API接口处理消息存储到物理硬盘和查询功能。
  - **HA Service**：高可用服务，提供Master Broker 和 Slave Broker之间的数据同步功能。
  - **Index Service**：根据特定的Message key对投递到Broker的消息进行索引服务，以提供消息的快速查询。

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657371288878-879ab2f2-4502-48cb-b169-c410bdee74bd.png)

## producer

### 生产模式

rocketMQ支持，`同步消息`、`异步消息`和`单向消息`。其中前两种消息是可靠的，因为会有发送是否成功的应答，单向消息一般用于心跳包的发送。

```java
void sendOneway(final Message msg) ;

SendResult send(final Message msg);
 
void send(final Message msg, final SendCallback sendCallback);
```

### 分区选择

rocketMQ中，一个topic会有多个MessageQueue，MessageQueue还有可能在不同的broker上，发送时消息会落在那个MessageQueue上呢？

在默认的情况下消息发送会采取Round Robin轮询方式把消息发送到不同的queue(分区队列)，同时我们也可以在发送消息的时候去指定分区，或者指定一个分区算法。

```java
SendResult send(final Message msg, final MessageQueue mq);
SendResult send(final Message msg, final MessageQueueSelector selector, final Object arg);
```

### 顺序消息

rocketMQ可以保证单分区内的消息严格有序，顺序消息又包括**分区有序**和**全局有序**。分区有序比如同一个订单的创建，支付，发货等状态管理，我们希望其有序，可以自定义分区选择器，保证相同订单的消息发送到同一个分区中。如果想实现全局有序，我们需要将所有消息都发送到同一个分区队列中，也就是只用一个分区。

### 延迟消息

延迟消息是指消息发送到broker后，不会立即被消费，等待特定时间投递给真正的topic。 broker有配置项messageDelayLevel，默认值为“1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h”，18个level。可以配置自定义messageDelayLevel。注意，messageDelayLevel是broker的属性，不属于某个topic。发消息时，设置delayLevel等级即可。

延迟消息的原理：在发送消息时，发现是延迟消息，就会**替换topic**，将消息暂存在名为SCHEDULE_TOPIC_XXXX的topic中，并根据delayTimeLevel存入特定的queue，queueId = delayTimeLevel – 1，即一个queue只存相同延迟的消息，保证具有相同发送延迟的消息能够顺序消费。broker会调度地消费SCHEDULE_TOPIC_XXXX，将消息写入真实的topic。

需要注意的是，定时消息会在第一次写入和调度写入真实topic时都会计数，因此发送数量、tps都会变高。

### 事务消息

Apache RocketMQ在4.3.0版中已经支持分布式事务消息，这里RocketMQ采用了2PC的思想来实现了提交事务消息，同时增加一个补偿逻辑来处理二阶段超时或者失败的消息，如下图所示。

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657347607671-294b014d-b42d-4907-8534-5874c0e6789c.png)

上图说明了事务消息的大致方案，其中分为两个流程：正常事务消息的发送及提交、事务消息的补偿流程。

1.**事务消息发送及提交**：

(1) 发送消息（half消息）。

(2) 服务端响应消息写入结果。

(3) 根据发送结果执行本地事务（如果写入失败，此时half消息对业务不可见，本地逻辑不执行）。

(4) 根据本地事务状态执行Commit或者Rollback（Commit操作生成消息索引，消息对消费者可见）

2.**补偿流程**：

(1) 对没有Commit/Rollback的事务消息（pending状态的消息），从服务端发起一次“回查”

(2) Producer收到回查消息，检查回查消息对应的本地事务的状态

(3) 根据本地事务状态，重新Commit或者Rollback

其中，补偿阶段用于解决消息Commit或者Rollback发生超时或者失败的情况。

**如何保证事务未提交消费者不可见？**
 RocketMQ事务消息的做法是：如果消息是**half消息**，将备份原消息的主题与消息消费队列，然后**改变主题**为RMQ_SYS_TRANS_HALF_TOPIC。由于消费组未订阅该主题，故消费端无法消费half类型的消息，然后RocketMQ会开启一个定时任务，从Topic为RMQ_SYS_TRANS_HALF_TOPIC中拉取消息进行消费，根据生产者组获取一个服务提供者发送回查事务状态请求，根据事务状态来决定是提交或回滚消息。

事实上，哪怕是回滚的事务，我们也没法删除half消息。RocketMQ事务消息方案中引入了**Op消息**的概念，用Op消息标识事务消息已经确定的状态。用op消息来索引half消息的offset，并将commit的消息发送到原消息topic中。

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657348046159-8d910c07-bcae-4351-a385-fd80916d1aa3.png)

二阶段提交失败的消息，broker会进行一个回查，回查的接口也是producer在实现事务消息的时候就实现的接口，如果回查失败，broker只会进行15次重试。

## broker

### 集群模式

**(一)、单Master模式**

这种方式风险较大，一旦Broker重启或者宕机时，会导致整个服务不可用。不建议线上环境使用，可以用于本地测试。

**(二)、多 Master 模式**

一个集群无Slave，全是Master，例如2个Master或者3个Master，这种模式的优缺点如下：

`优点`：配置简单，单个Master宕机或重启维护对应用无影响，在磁盘配置为RAID10时，即使机器宕机不可恢复情况下，由于RAID10磁盘非常可靠，消息也不会丢（异步刷盘丢失少量消息，同步刷盘一条不丢），性能最高；

`缺点`：单台机器宕机期间，这台机器上未被消费的消息在机器恢复之前不可订阅，消息消费的实时性会受到影响。

**(三)、多Master多Slave模式-异步复制**

每个Master配置一个Slave，有多对Master-Slave，HA采用异步复制方式，主备有短暂消息延迟（毫秒级），这种模式的优缺点如下：

`优点`：即使磁盘损坏，消息丢失的非常少，且消息实时性不会受影响，同时Master宕机后，消费者仍然可以从Slave消费，而且此过程对应用透明，不需要人工干预，性能同多Master模式几乎一样。

`缺点`：Master宕机，磁盘损坏情况下会丢失少量消息。

**(四)、多Master多Slave模式-同步双写**

每个Master配置一个Slave，有多对Master-Slave，HA采用同步双写方式，即只有主备都写成功，才向应用返回成功，这种模式的优缺点如下：

`优点`：数据与服务都无单点故障，Master宕机情况下，消息无延迟，服务可用性与数据可用性都非常高；

`缺点`：性能比异步复制模式略低（大约低10%左右），发送单个消息的RT会略高。

### 可靠性保证

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657348538828-0a5cb1f6-61ba-43a1-b456-b72baacec3a5.png)

异步刷盘：是指消息存入pagecache后就视为成功，返回应答。如果os crash消息有可能丢失。

同步刷盘：是指消息需要持久化到磁盘后才视为成功，返回应答。

主从同步复制：在高可用模式下，等Master和Slave均写 成功后才反馈给客户端写成功状态，如果主节点出现异常，从节点还有所有的数据。

主从异步复制：在高可用模式下，等Master写成功后就反馈给客户端写成功状态

一般生产环境下，为了皮面频繁刷盘导致性能下降，我们一般由主从的方式保证数据可靠性，开启异步刷盘和主从同步复制。

### 消息存储

![img](https://cdn.nlark.com/yuque/0/2022/png/26318626/1657371502575-cbc03dd2-5488-44ba-8e8f-c8472addfd0b.png)

消息存储架构图中主要有下面三个跟消息存储相关的文件构成。

(1)` CommitLog`：消息主体以及元数据的存储主体，存储Producer端写入的消息主体内容,消息内容不是定长的。单个文件大小默认1G, 文件名长度为20位，左边补零，剩余为起始偏移量，比如00000000000000000000代表了第一个文件，起始偏移量为0，文件大小为1G=1073741824；当第一个文件写满了，第二个文件为00000000001073741824，起始偏移量为1073741824，以此类推。消息主要是顺序写入日志文件，当文件满了，写入下一个文件；

(2) `ConsumeQueue`：消息消费队列，引入的目的主要是提高消息消费的性能，由于RocketMQ是基于主题topic的订阅模式，消息消费是针对主题进行的，如果要遍历commitlog文件中根据topic检索消息是非常低效的。Consumer即可根据ConsumeQueue来查找待消费的消息。其中，ConsumeQueue（逻辑消费队列）作为消费消息的索引，保存了指定Topic下的队列消息在CommitLog中的起始物理偏移量offset，消息大小size和消息Tag的HashCode值。**consumequeue文件可以看成是基于topic的commitlog索引文件**，故consumequeue文件夹的组织方式如下：topic/queue/file三层组织结构，具体存储路径为：$HOME/store/consumequeue/{topic}/{queueId}/{fileName}。同样consumequeue文件采取定长设计，每一个条目共20个字节，分别为8字节的commitlog物理偏移量、4字节的消息长度、8字节tag hashcode，单个文件由30W个条目组成，可以像数组一样随机访问每一个条目，每个ConsumeQueue文件大小约5.72M；

(3) `IndexFile`：IndexFile（索引文件）提供了一种可以通过key或时间区间来查询消息的方法。Index文件的存储位置是：HOME\store\index{fileName}，文件名fileName是以创建时的时间戳命名的，固定的单个IndexFile文件大小约为400M，一个IndexFile可以保存 2000W个索引，IndexFile的底层存储设计为在文件系统中实现HashMap结构，故rocketmq的索引文件其底层实现为hash索引。

(4)`offset`：offset用来管理每个消费队列的不同消费组的消费进度。对offset的管理分为本地模式和远程模式，本地模式是以文本文件的形式存储在客户端，而远程模式是将数据保存到broker端，对应的数据结构分别为LocalFileOffsetStore和RemoteBrokerOffsetStore。
默认情况下，当消费模式为广播模式时，offset使用本地模式存储，因为每条消息会被所有的消费者消费，每个消费者管理自己的消费进度，各个消费者之间不存在消费进度的交集；当消费模式为集群消费时，则使用远程模式管理offset，消息会被多个消费者消费，不同的是每个消费者只负责消费其中部分消费队列，添加或删除消费者，都会使负载发生变动，容易造成消费进度冲突，因此需要集中管理。同时，RocketMQ也提供接口供用户自己实现offset管理（实现OffsetStore接口）。

rocketMQ的broker端中，offset的是以json的形式持久化到磁盘文件中，文件路径为`${user.home}/store/config/consumerOffset.json`。其内容示例如下：

```java
{
    "offsetTable": {
        "test-topic@test-group": {
            "0": 88526, 
            "1": 88528
        }
    }
}
```

在上面的RocketMQ的消息存储整体架构图中可以看出，RocketMQ采用的是混合型的存储结构，即为Broker单个实例下所有的队列共用一个日志数据文件（即为CommitLog）来存储。RocketMQ的混合型存储结构(多个Topic的消息实体内容都存储于一个CommitLog中)针对Producer和Consumer分别采用了数据和索引部分相分离的存储结构，Producer发送消息至Broker端，然后Broker端使用同步或者异步的方式对消息刷盘持久化，保存至CommitLog中。只要消息被刷盘持久化至磁盘文件CommitLog中，那么Producer发送的消息就不会丢失。正因为如此，Consumer也就肯定有机会去消费这条消息。当无法拉取到消息后，可以等下一次消息拉取，同时服务端也支持长轮询模式，如果一个消息拉取请求未拉取到消息，Broker允许等待30s的时间，只要这段时间内有新消息到达，将直接返回给消费端。这里，RocketMQ的具体做法是，使用Broker端的后台服务线程—ReputMessageService不停地分发请求并异步构建ConsumeQueue（逻辑消费队列）和IndexFile（索引文件）数据。

### 页缓存和内存映射

页缓存（PageCache)是OS对文件的缓存，用于加速对文件的读写。

RocketMQ主要通过`MappedByteBuffer`对文件进行读写操作。其中，利用了NIO中的`FileChannel`模型将磁盘上的物理文件直接映射到用户态的内存地址中（这种**Mmap**的方式减少了传统IO将磁盘文件数据在操作系统内核地址空间的缓冲区和用户应用程序地址空间的缓冲区之间来回进行拷贝的性能开销），将对文件的操作转化为直接对内存地址进行操作，从而极大地提高了文件的读写效率（正因为需要使用内存映射机制，故RocketMQ的文件存储都使用定长结构来存储，方便一次将整个文件映射至内存）

## consumer

### 消费方式

rocketMQ有两种消息方式，pull和push。

**push**

`优点`：

1. 服务端主动给客户端推送消息，实时性高，体验好。
2. 屏蔽了消息获取的细节（分区，offset），使用起来方便。

`缺点`：

1. 服务端推送超过了客户端的负载能力，会照成消息堆积，占用客户端资源，不过rocketmq采用了**流控策略。**

**pull**

`优点`：

1.想消费多少就消费多少，想怎么消费就怎么消费，灵活性较大，不存在过多占用消费者资源的问题

`缺点`：

1.实时性很低

2.拉取消息的间隔不好设置，太短则borker压力大，太长则实时性很低。

生产情况，我们一般都采用push的方式。

RocketMQ的push模式不是严格意义上的推，是通过后台启动异步线程，一个queue构建一个pullRequest， **异步的去请求的拉模式**，只不过是通过broker端阻塞（默认阻塞15秒）的方法，达到了推模式的效果。 其实就是长轮询模式。

为了避免push对客户端照成大量的负担，rocketMQ还有消费者流控：

- 消费者本地缓存消息数超过pullThresholdForQueue时，默认1000。
- 消费者本地缓存消息大小超过pullThresholdSizeForQueue时，默认100MB。
- 消费者本地缓存消息跨度超过consumeConcurrentlyMaxSpan时，默认2000。

### 标签过滤

生产者可以给消息打上标签，消费者消费的时候也可以指定标签过滤。标签过滤在broker端进行的，减少了**无用的消息在网络中的传输成本**。

标签过滤有两种方式，一种直接指定标签，第二种采用sql表达式的方式。

### 消息重试

Consumer消费消息失败后，要提供一种重试机制，令消息再消费一次。Consumer消费消息失败通常可以认为有以下几种情况：

- 由于消息本身的原因，例如反序列化失败，消息数据本身无法处理（例如话费充值，当前消息的手机号被注销，无法充值）等。这种错误通常需要跳过这条消息，再消费其它消息，而这条失败的消息即使立刻重试消费，99%也不成功，所以最好提供一种定时重试机制，即过10秒后再重试。
- 由于依赖的下游应用服务不可用，例如db连接不可用，外系统网络不可达等。遇到这种错误，即使跳过当前失败的消息，消费其他消息同样也会报错。这种情况建议应用sleep 30s，再消费下一条消息，这样可以减轻Broker重试消息的压力。

RocketMQ会为每个消费组都设置一个Topic名称为“**%RETRY%+consumerGroup**”的重试队列（这里需要注意的是，这个Topic的重试队列是**针对消费组**，而不是针对每个Topic设置的），用于暂时保存因为各种异常而导致Consumer端无法消费的消息。考虑到异常恢复起来需要一些时间，会为重试队列设置多个重试级别，每个重试级别都有与之对应的重新投递延时，重试次数越多投递延时就越大。RocketMQ对于重试消息的处理是先保存至Topic名称为“**SCHEDULE_TOPIC_XXXX**”的延迟队列中，后台定时任务按照对应的时间进行Delay后重新保存至“**%RETRY%+consumerGroup**”的重试队列中。

可以看出来，rocketMQ中大量用到了替换topic方式来实现特性功能，比如**延迟消息**，**重试消息**，**事务消息**，**死信队列**

### 顺序消费

采用顺序消息，首先需要producer将消息发送到同一个分区内，消费者采用MessageListenerOrderly对顺序消息进行处理。

Consumer 消费消息的逻辑如下：

1. 对 MessageQueueLock 进行加锁，这样就保证只有一个线程在处理当前 MessageQueue；
2. 从 ProcessQueue 拉取一批消息；
3. 获取 ProcessQueue 锁，这样保证了只有当前线程可以进行消息处理，同时也可以防止 Rebalance 线程把当前处理的 MessageQueue 移除掉；
4. 执行消费处理逻辑；
5. 释放 ProcessQueue 处理锁；6.processConsumeResult 方法更新消息偏移量。

跟并发消息不一样的是，顺序消息消费失败后并不会把消息发送到 Broker，而是直接在 **Consumer 端进行重试**，如果重试次数超过了最大重试次数（16 次），则发送到 Broker，Broker 则将消息推入死信队列。

## 参考资料

[官方中文文档](https://github.com/apache/rocketmq/tree/master/docs/cn)

[CSDN精讲](https://blog.csdn.net/zhuocailing3390/category_11621856.html)

[offset管理链接](https://www.cnblogs.com/hanease/p/15937549.html)