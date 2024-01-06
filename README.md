
## 检验账号密码是否正确的接口
### /frontapi/user/login接口描述
功能：检验账号与密码是否正确  
url地址：http://localhost:3000/frontapi/user/login  
请求工具：PostMan  
请求方式：POST 
请求参数格式：JSON  
注意事项：fingerPrintJs为浏览器指纹，有点类似mac地址
响应参数：  
{  
"username":"123",  
"password":"123",
 "fingerPrintJs":"XXX"
}  
响应数据：
成功  
{  
"code": 200,  
"message": "测试成功",  
"data": {  
"token": "XXX"  
}  
}  
失败：  
{  
"code": 503,  
"message": "密码错误",  
"data": null  
}

## 检验token是否有效的端口
### /frontapi/user/getUserInfo 端口描述
功能:接收前端发送过来的请求，判断token是否过期，
并且返回token所对应的账号与密码  
url地址：http://localhost:3000/frontapi/user/getUserInfo  
请求工具：postman  
请求方式：GET  
请求头：  
token：token内容  
响应参数：  
成功：  
{
"code": 200,  
"message": "测试成功",  
"data":{  
"loginUser": {  
"uid": 2,  
"username": "123",  
"password": "123"  
}  
}  
失败：  
{  
"code": 504,  
"message": "notLogin",  
"data": null  
}

## 检查用户是否存在的端口
### /frontapi/user/checkUserName 端口描述
功能:判断账号是否已经存在  
url地址：http://localhost:3000/frontapi/user/checkUserName  
请求工具：postman  
请求方式： POST  
请求参数：Params  
key：username  
value：用户名  
响应参数：  
成功：  
{  
"code": 200,  
"message": "测试成功",  
"data": null  
}  
失败：  
{  
"code": 505,  
"message": "用户已经存在",  
"data": null  
}
## 注册用的端口
### /frontapi/user/register 端口描述
功能：将前端发送的用户数据存储到数据库中  
注意事项：用户使用postman传送数据时，请求头Headers中加入
Content-Type ：application/json;charset=UTF-8
来防止数据库存储失败  
url地址：http://localhost:3000/frontapi/user/register  
请求工具：postman  
请求方式POst  
请求参数：{  
"username":"czc",  
"password":"123"  
}  
响应参数  
成功：  
{  
"code": 200,  
"message": "测试成功",  
"data": null  
}  
失败：  
{  
"code": 505,  
"message": "用户已经存在",  
"data": null  
}
## 发送验证码的端口
### /frontapi/user/code
功能：点击按钮，生成一个验证码，放入redis中，检验验证码是否正确  
方法：Post  
请求参数：Params  
phone:手机号 
响应参数:  
成功  
{  
"code":200,  
"message":"测试成功"  
}
失败:  
无返回

## 手机登录注册的接口
### /frontapi/user/phoneLogin
功能：接收手机号与验证码，与存储在redis中的进行判断，是否正确，
正确就进行注册登录
方法：Post
请求参数:  
{  
"phone":"18966043274",  
"code":"230208"   
}
响应参数:  
{  
"code": 200,  
"message": "测试成功",  
"data": "47c64d77ea744434841bf26951a6b6d7"  
}

## 修改密码端口  
### /frontapi/user/passwordChange  
功能：修改用户的密码  
方法：Post  
请求参数：  
{  
"username":"xjf",  
"old_password":"xjf",  
"new_password":"xjf"  
}
响应参数：  
{  
"code": 200,  
"message": "测试成功"  
}
