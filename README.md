# data_pojo_tools

一键生成数据库实体类的命令行工具 打包后 执行 java -jar data_pojo_tools-1.0.jar

只支持mysql 配合 alias 设置全局命令

需在带生成项目的路径下配置一下 文件 文件名 pojo_config.json

可同时生成多个schema的pojo
```javascript
{
  "prefix": "",
  "suffix": "Entity",
  "schemas": [
    {
      "ip": "127.0.0.1",
      "port": "3306",
      "user": "root",
      "password": "xxxx",
      "schema": "master1",
      "package": "com.ims.entity.po.master"
    },
    {
      "ip": "127.0.0.2",
      "port": "3306",
      "user": "root",
      "password": "xxxx",
      "schema": "master2",
      "package": "com.ims.entity.po.master"
    }
  ]
}
```
生成的pojo 结合了 lombok 利用 @Data 简化 文件结构
