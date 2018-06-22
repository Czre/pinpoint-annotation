## thrift

必要项

 - 这个包是用来定义netty传输数据格式,如果需要修改传输的格式 profiler/collector/thrift都会需要做修改

 - 如果需要修改T*.java文件,直接修改thrift/src/main/thrift/*.thrift文件即可,根据项目所处环境使用不同的build-thrift-*.*文件即可(之前是使用搭建有thrift环境的虚拟机编译的.)