## profiler-optional

profiler-optional下的模块包含用于profiler的可选包，包含必须针对特定版本的JDK进行编译的功能和代码。

此外，这些可选模块可能包含针对特定供应商的存根类编译。
这些类不包含在最终的jar包中，针对这些存根编译的供应商特定实现必须使用供应商提供的实现进行加载。
