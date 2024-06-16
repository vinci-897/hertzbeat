---
id: alert_threshold_expr  
title: 阈值触发表达式  
sidebar_label: 阈值触发表达式
---
> 在我们配置阈值告警时，需要配置阈值触发表达式，系统根据表达式和监控指标值计算触发是否告警，这里详细介绍下表达式使用。

#### 表达式支持的操作符


| 运算符(可视化配置) | 运算符(表达式配置)   | 支持类型                | 说明                     |  |
| ------------------ | -------------------- | ----------------------- | ------------------------ | - |
| 等于               | equals(str1,str2)    | 字符型                  | 判断字符串是否相等       |  |
| 不等于             | !equals(str1,str2)   | 字符型                  | 判断字符串是否不相等     |  |
| 包含               | contains(str1,str2)  | 字符型                  | 判断字符串是否包含       |  |
| 不包含             | !contains(str1,str2) | 字符型                  | 判断字符串是否不包含     |  |
| 匹配               | matches(str1,str2)   | 字符型                  | 判断字符串正则是否匹配   |  |
| 不匹配             | !matches(str1,str2)  | 字符型                  | 判断字符串正则是否不匹配 |  |
| 存在值             | exists(obj)          | 字符型、数值型、时间型  | 判断字符是否有值存在     |  |
| 不存在值           | !exists(obj)         | 字符型 、数值型、时间型 | 判断字符是否有值存在     |  |
| >                  | obj1 > obj2          | 数值型、时间型          | 判断数值是否大于         |  |
| <                  | obj1 < obj2          | 数值型、时间型          | 判断数值是否小于         |  |
| >=                 | obj1 >= obj2         | 数值型、时间型          | 判断数值是否大于等于     |  |
| <=                 | obj1 <= obj2         | 数值型、时间型          | 判断数值是否小于等于     |  |
| !=                 | obj1 != obj2         | 数值型、时间型          | 判断数值是否不相等       |  |
| ==                 | obj1 == obj2         | 数值型、时间型          | 判断数值是否相等         |  |

#### 表达式函数库列表


| 支持函数库                                   | 说明                                                               |
| -------------------------------------------- | ------------------------------------------------------------------ |
| condition ? trueExpression : falseExpression | 三元运算符                                                         |
| toDouble(str)                                | 将字符串转换为Double类型                                           |
| toBoolean(str)                               | 将字符串转换为Boolean类型                                          |
| toInteger(str)                               | 将字符串转换为Integer类型                                          |
| array[n]                                     | 取数组第n个元素                                                    |
| *                                            | 算法乘                                                             |
| /                                            | 算法除                                                             |
| %                                            | 求余                                                               |
| ( 和 )                                       | 括号用于控制运算的优先级，确保在逻辑或数学表达式中某些部分先被计算 |
| +                                            | 加号用于表示数学中的加法运算、字符串拼接                           |
| -                                            | 减号用于表示数学中的减法运算                                       |
| &&                                           | 逻辑AND操作符                                                      |
| \|\|                                         | 逻辑OR操作符                                                       |

#### 支持的环境变量

> 环境变量即指标值等支持的变量，用于在表达式中，阈值计算判断时会将变量替换成实际值进行计算

非固定环境变量：这些变量会根据我们选择的监控指标对象而动态变化，例如我们选择了**网站监控的响应时间指标**，则环境变量就有 `responseTime - 此为响应时间变量`
如果我们想设置**网站监控的响应时间大于400时**触发告警，则表达式为 `responseTime>400`

固定环境变量(不常用)：`instance : 所属行实例值`
此变量主要用于计算多实例时，比如采集到c盘d盘的`usage`(`usage为非固定环境变量`),我们只想设置**c盘的usage大于80**时告警，则表达式为 `equals(instance,"c")&&usage>80`

#### 表达式设置案例

1. 网站监控->响应时间大于等于400ms时触发告警
   `responseTime>=400`
2. API监控->响应时间大于3000ms时触发告警
   `responseTime>3000`
3. 全站监控->URL(instance)路径为 `https://baidu.com/book/3` 的响应时间大于200ms时触发告警
   `equals(instance,"https://baidu.com/book/3")&&responseTime>200`
4. MYSQL监控->status指标->threads_running(运行线程数)指标大于7时触发告警
   `threads_running>7`

若遇到问题可以通过交流群ISSUE交流反馈哦！