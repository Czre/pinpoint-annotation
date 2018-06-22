##plugin

必要项：
  必须了解serviceloader加载机制,将会在agent加载的过程中加载plugin包,而这个包将会由maven打包进agent,collector,web中
  > 所有的插件都是通过META-INF/services定义的com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin和com.navercorp.pinpoint.common.trace.TraceMetadataProvider来指定service加载的类
  
  - com.navercorp.pinpoint.bootstrap.plugin.ProfilerPlugin定义插件类去转换(字节码植入)的一些方法或者类
  - com.navercorp.pinpoint.common.trace.TraceMetadataProvider定义该插件的serverType类型和AnnotationKey的数值,以便在数据采集的时候作为一个记录
   
  - 建议在每个插件中读取config配置文件以便形成开关模式
  
   