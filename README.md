##1.概述

必要项：

 - 通过支持的plugin 从代码层级发现问题，耗时慢或错误等问题，另外还收集了部署agent的服务器性能数据

 - Application Project Manajer？

 - 运维人员？Java开发人员？
    - 方便需要的人员定位问题，然后解决问题
    - 通过部署agent，由agent收集服务器相关数据，发送至collector层（自己启动的服务），接收数据落库，然后由web层查询数据库数据，并处理，返回给前端。
    - 主要通过JDK1.6的Instrumentation机制，加载核心jar包(insight-bootstrap.jar)与字节码增强技术(javassist)，当进入到支持的plugin时，在支持代码或方法执行之前与之后添加需要获取的参数，记录下来使用thrift压缩并通过Netty通信框架发送数据给指定IP(部署并运行了自身的collector层)
 
##2.概要设计：

- 功能模块说明
    - agent 负责类的加载与字节码植入，并创建netty通信框架，使用thrift来作为传输的数据
    - collector 负责接收agent发送的数据，并将数据存入数据库中
    - web 负责从数据库拿出数据，在页面展示数据

- 模块概要说明
   - agent 主要使用maven来将annotations，commons，bootstrap，bootstrap-core，bootstrap-core-optional，plugins，tools等子项目的jar包引入进来
   - annotations 字面意思是注解，具体做什么没去看
   - bootstrap agent开始的地方，通过JDK6 instrumentation机制，在容器启动或者JVM启东时添加 -javaagent参数 引入bootstrap.jar包，加载通过编译输出的agent文件夹下面的相关jar包。主要入口为PinpointBootStrap.premain()方法
   - bootstrap-core 加载启动的核心项目，具体做说明没去看
   - bootstrap-core-optional JDK的类加载器？其中一个类重写来加载类的方式，这个项目下面只有三个类
   - collector 主要功能是接收数据并落库。将接收到的Thrift类型的数据，通过map方式转换成Bo类，存入数据库
   - commons 常用组件
   - commons-server 服务组件项目
   - doc 文档。没怎么去看过
   - hbase 建表删表脚本
   - plugins pinpoint的另一大核心，支持的一些插件包，web容器，中间件等等，配合bootstrap-core项目来转换一些方法，完成字节码植入的步骤，获取数据。
   - profiler agent植入之后配合plugins获取到一些支持的插件执行之前和之后的方法的数据，在这里组合，并创建通信，构成RPC传输的数据，使用UDP和TCP协议向collector发送数据。(使用guice依赖注入，Netty通信，Thrift RPC工具)
   - profiler-optional jdk获取cpu的补充项目，jdk6采用的手动计算方式来获取cpu资源，jdk7及以上是通过ManagementFactory来获取一些数据(PS：ManagementFactory可以获取很多的数据但是只有1.7及以上才支持)
   - profiler-test profiler的测试类
   - rpc 通信传输相关的项目 具体做什么没去看
   - test 整个项目的测试
   - thrift rpc工具定义类一些thrift的文件和支持thrift的java类，正常操作定义好thrift相关文件，使用thrift/build-thrift-*.*文件编译或者使用带有thrift的环境编译出java的文件即可
   - tools 用来测试是否ping的通collector层的端口
   - web 读取数据展示
   