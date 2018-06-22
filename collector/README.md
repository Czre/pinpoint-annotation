## collector

接收agent发送过来的数据,以zookeeper形成的分布式方式接收存储数据
流程:agent->UDP/TCP Data-> handler接收转换-> 调用dao存储
