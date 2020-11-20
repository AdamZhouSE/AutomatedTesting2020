# AutomatedTesting2020

自动化测试——经典自动化测试大作业：

使用工具WALA完成静态分析，构建代码与测试代码之间的联系，得到代码的依赖图并根据代码关系图和变更信息，筛选出受变更的测试。

**目录结构**

```
.
├── ./Data				存放测试数据
├── ./Demo				项目生成的jar包
├── ./Project			项目源代码
├── ./README.md
└── ./Report			存放代码依赖图
```

**使用**

```shell
# 类级测试选择
java -jar testSelection.jar -c <project_target> <change_info>
# 方法级测试选择
java -jar testSelection.jar -m <project_target> <change_info>
```

`<project_target> `是待测项目`target`文件，比如`./Data/1-ALU/target`

`<change_info>`是记录变更信息的的文本文件，比如`./Data/1-ALU/data/change_info.txt`

