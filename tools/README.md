## tools

必要项
 
 - 这个包主要是用来测试agent包是否能ping通collector层启动的服务 通过insight.config设置的ip地址 和端口号(默认 ip:127.0.0.1 port: 9994/9995/9996 1.7以上版本貌似还增加了9997端口尚未认证)
 
 - java包定义shell脚本执行的文件
 
 - script定义执行脚本
 
 - 如果有需要,请更改networktest.sh最后一行 更改pinpoint.config 该参数将由NetworkAvailabilityChecker.java的main函数传入 并执行一系列认证方法 来进行端口认证