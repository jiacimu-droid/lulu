// OrangeChat 插件开发指南 - 让 AI 掌握插件开发知识

function get_plugin_docs(params) {
  var topic = (params.topic || "quickstart").toLowerCase();
  
  var docs = {
    quickstart: QUICKSTART,
    manifest: MANIFEST_DOC,
    mainjs: MAINJS_DOC,
    sandbox: SANDBOX_DOC,
    declarative_ui: UI_DOC,
    ui_components: UI_COMPONENTS_DOC,
    ui_actions: UI_ACTIONS_DOC,
    ui_queries: UI_QUERIES_DOC,
    config: CONFIG_DOC,
    prompt: PROMPT_DOC,
    full_spec: FULL_SPEC
  };
  
  var content = docs[topic];
  if (!content) {
    content = "未知主题: " + topic + "\n可用主题: quickstart, manifest, mainjs, sandbox, declarative_ui, ui_components, ui_actions, ui_queries, config, prompt, full_spec";
  }
  
  return { success: true, topic: topic, content: content };
}

var QUICKSTART = "\
# OrangeChat 插件快速开始\n\
\n\
## 插件是什么？\n\
插件是扩展 AI 能力的模块，让 AI 可以调用外部工具、获取实时数据。用户只需把 manifest.json + main.js 打包成 zip 导入 App 即可。\n\
\n\
## 最简插件示例\n\
\n\
### manifest.json\n\
```json\n\
{\n\
  \"id\": \"com.example.hello\",\n\
  \"name\": \"打招呼\",\n\
  \"description\": \"简单打招呼插件\",\n\
  \"version\": \"1.0.0\",\n\
  \"author\": \"YourName\",\n\
  \"icon\": \"👋\",\n\
  \"entry\": \"main.js\",\n\
  \"tools\": [{\n\
    \"name\": \"say_hello\",\n\
    \"description\": \"向用户打招呼\",\n\
    \"parameters\": [\n\
      {\"name\": \"name\", \"type\": \"string\", \"required\": true, \"description\": \"用户名字\"}\n\
    ]\n\
  }]\n\
}\n\
```\n\
\n\
### main.js\n\
```javascript\n\
function say_hello(params) {\n\
  var name = params.name || 'World';\n\
  return { success: true, message: 'Hello, ' + name + '!' };\n\
}\n\
exports.say_hello = say_hello;\n\
```\n\
\n\
## 打包导入\n\
1. 创建文件夹，放入 manifest.json 和 main.js\n\
2. 压缩为 zip 文件\n\
3. 在 App 插件管理页导入 zip\n\
\n\
## 关键规则\n\
- manifest.tools[].name 必须与 exports.xxx 完全一致\n\
- fetch 是同步的，不需要 await\n\
- 不要用 async/await\n\
- 返回值建议包含 success 字段\n\
";

var MANIFEST_DOC = "\
# manifest.json 完整规范\n\
\n\
## 必需字段\n\
- id: 唯一标识，反向域名格式如 com.example.plugin.name\n\
- name: 显示名称\n\
- description: 插件描述\n\
- version: 版本号如 1.0.0\n\
- author: 作者\n\
- icon: emoji 或 URL\n\
- entry: 入口文件路径，通常为 main.js\n\
\n\
## 可选字段\n\
- tools: 工具定义列表（让AI调用的函数）\n\
- config: 配置字段列表（用户设置参数）\n\
- promptTemplate: 提示词模板字符串\n\
- ui: 声明式UI定义（原生Compose渲染）\n\
- customPageWebView: WebView页面配置\n\
- customPage: 内置页面标识\n\
\n\
## tools 工具定义\n\
```json\n\
\"tools\": [{\n\
  \"name\": \"tool_name\",          // 必须与main.js exports一致\n\
  \"description\": \"工具描述\",     // AI据此决定何时调用\n\
  \"parameters\": [{\n\
    \"name\": \"param_name\",\n\
    \"type\": \"string\",           // string/integer/boolean/object/array\n\
    \"required\": true,\n\
    \"description\": \"参数描述\"\n\
  }]\n\
}]\n\
```\n\
\n\
## UI优先级\n\
ui(声明式) > customPageWebView > customPage\n\
";

var MAINJS_DOC = "\
# main.js 编写规范\n\
\n\
## 基本结构\n\
```javascript\n\
// params: AI调用时传入的参数对象\n\
// config: 用户配置值（自动注入）\n\
\n\
function my_tool(params) {\n\
  var input = params.input || '';\n\
  var limit = config.max_results || 10;\n\
  \n\
  // 处理逻辑...\n\
  \n\
  return { success: true, data: '结果' };\n\
}\n\
\n\
// 必须导出！名称与manifest tools[].name一致\n\
exports.my_tool = my_tool;\n\
```\n\
\n\
## 多个工具\n\
```javascript\n\
function tool_a(params) { /* ... */ }\n\
function tool_b(params) { /* ... */ }\n\
\n\
exports.tool_a = tool_a;\n\
exports.tool_b = tool_b;\n\
```\n\
\n\
## 返回值约定\n\
- 成功: { success: true, ... }\n\
- 失败: { success: false, error: '错误信息' }\n\
\n\
## 重要规则\n\
1. fetch是同步的 - 不需要await，直接var res = fetch(url)\n\
2. 不要用async/await - 沙箱会移除这些关键字\n\
3. 函数名必须与manifest tools完全一致\n\
4. 使用var而非let/const（QuickJS兼容性更好）\n\
5. JSON.parse()可正常使用\n\
6. console.log()输出到Android Log\n\
";

var SANDBOX_DOC = "\
# 沙箱API参考\n\
\n\
## fetch(url) - 同步HTTP GET\n\
```javascript\n\
var response = fetch('https://api.example.com/data');\n\
// response: { success, status, ok, headers, body }\n\
if (response.ok) {\n\
  var data = JSON.parse(response.body);\n\
}\n\
```\n\
\n\
## fetch(url, options) - 同步HTTP请求\n\
```javascript\n\
var response = fetch('https://api.example.com/items', {\n\
  method: 'POST',\n\
  body: JSON.stringify({ name: 'test' }),\n\
  headers: {\n\
    'Content-Type': 'application/json',\n\
    'Authorization': 'Bearer ' + config.api_key\n\
  }\n\
});\n\
```\n\
支持: GET/POST/PUT/DELETE，超时15秒\n\
\n\
## config - 用户配置对象\n\
```javascript\n\
var apiKey = config.api_key;       // string/password\n\
var baseUrl = config.base_url;     // string\n\
var max = config.max_results;      // integer\n\
var auto = config.auto_save;       // boolean\n\
```\n\
\n\
## console - 日志\n\
console.log/info/warn/error → Android Logcat\n\
";

var UI_DOC = "\
# 声明式UI (ui字段)\n\
\n\
通过manifest.json的ui字段声明UI组件，App渲染为原生Compose Material 3界面。\n\
\n\
## 结构\n\
```json\n\
\"ui\": {\n\
  \"title\": \"📦 数据管理\",\n\
  \"queries\": { ... },\n\
  \"actions\": { ... },\n\
  \"components\": [ ... ]\n\
}\n\
```\n\
\n\
## 数据流\n\
1. queries从PluginDataStore查询数据\n\
2. components通过queryName引用查询结果渲染\n\
3. 用户交互触发actions修改数据\n\
4. action成功后按onSuccess刷新查询\n\
\n\
## 优势\n\
- 原生Material 3渲染，体验流畅\n\
- 无需前端开发经验\n\
- 自动适配深色/浅色主题\n\
\n\
可用子主题: ui_components / ui_actions / ui_queries\n\
";

var UI_COMPONENTS_DOC = "\
# UI组件参考\n\
\n\
## stats - 统计卡片行\n\
{\"type\":\"stats\",\"props\":{\"items\":[{\"label\":\"总数\",\"queryName\":\"list\",\"compute\":\"count\"},{\"label\":\"分类\",\"queryName\":\"list\",\"compute\":\"unique\",\"field\":\"category\"},{\"label\":\"固定值\",\"value\":\"42\"}]}}\n\
\n\
## search_bar - 搜索栏\n\
{\"type\":\"search_bar\",\"props\":{\"placeholder\":\"搜索...\"}}\n\
\n\
## filter_bar - 过滤标签栏\n\
{\"type\":\"filter_bar\",\"props\":{\"queryName\":\"list\",\"filterField\":\"category\",\"allLabel\":\"全部\"}}\n\
\n\
## card_grid - 卡片网格\n\
{\"type\":\"card_grid\",\"props\":{\"queryName\":\"list\",\"columns\":2,\"imageField\":\"imageUrl\",\"titleField\":\"name\",\"subtitleField\":\"desc\",\"tagField\":\"category\",\"deleteAction\":\"delete\",\"deleteKeyField\":\"_key\"}}\n\
\n\
## card_list - 卡片列表\n\
{\"type\":\"card_list\",\"props\":{\"queryName\":\"list\",\"titleField\":\"name\",\"subtitleField\":\"desc\",\"tagField\":\"category\",\"deleteAction\":\"delete\",\"deleteKeyField\":\"_key\"}}\n\
\n\
## button_row - 按钮行\n\
{\"type\":\"button_row\",\"props\":{\"buttons\":[{\"label\":\"+ 添加\",\"action\":\"add\",\"variant\":\"primary\"},{\"label\":\"刷新\",\"variant\":\"secondary\"}]}}\n\
variant: primary/secondary/text\n\
\n\
## dialog_form - 弹窗表单\n\
{\"type\":\"dialog_form\",\"props\":{\"triggerLabel\":\"+ 添加\",\"triggerVariant\":\"primary\",\"title\":\"添加\",\"fields\":[{\"name\":\"name\",\"label\":\"名称\",\"type\":\"string\",\"required\":true},{\"name\":\"desc\",\"label\":\"描述\",\"type\":\"string\",\"multiline\":true},{\"name\":\"cat\",\"label\":\"分类\",\"type\":\"select\",\"default\":\"general\"},{\"name\":\"priority\",\"label\":\"优先级\",\"type\":\"integer\",\"default\":\"5\"},{\"name\":\"enabled\",\"label\":\"启用\",\"type\":\"boolean\"},{\"name\":\"photo\",\"label\":\"照片\",\"type\":\"image\"}],\"submitAction\":\"save\",\"submitLabel\":\"保存\"}}\n\
field.type: string/integer/boolean/select/image\n\
\n\
## text - 文本\n\
{\"type\":\"text\",\"props\":{\"content\":\"说明文字\",\"style\":\"body\"}}\n\
style: headline/title/body/caption\n\
\n\
## empty_state - 空状态\n\
{\"type\":\"empty_state\",\"props\":{\"queryName\":\"list\",\"message\":\"暂无数据\"}}\n\
";

var UI_ACTIONS_DOC = "\
# 操作定义 (actions)\n\
\n\
## 操作类型\n\
- dataStore_set: 写入数据，params: { key, value }\n\
- dataStore_delete: 删除数据，params: { key }\n\
- file_delete: 删除文件，params: { fileName }\n\
\n\
## 模板变量\n\
- ${field.xxx}: 引用表单字段值\n\
- ${key}: 引用当前数据项的key\n\
- ${form}: 引用整个表单JSON\n\
\n\
## onSuccess\n\
- \"refresh\": 刷新所有查询\n\
- \"refresh_queries\": 刷新指定查询(配合refreshQueries)\n\
- \"close_dialog\": 关闭弹窗并刷新\n\
- \"navigate_back\": 返回上一页\n\
- \"none\": 无操作\n\
\n\
## 示例\n\
```json\n\
\"actions\": {\n\
  \"save_item\": {\n\
    \"type\": \"dataStore_set\",\n\
    \"params\": {\"key\": \"item_${field.name}\", \"value\": \"${form}\"},\n\
    \"onSuccess\": \"refresh\"\n\
  },\n\
  \"delete_item\": {\n\
    \"type\": \"dataStore_delete\",\n\
    \"params\": {\"key\": \"${key}\"},\n\
    \"onSuccess\": \"refresh\",\n\
    \"confirmDialog\": {\"title\": \"删除确认\", \"message\": \"确定删除吗？\"}\n\
  }\n\
}\n\
```\n\
";

var UI_QUERIES_DOC = "\
# 查询定义 (queries)\n\
\n\
## 查询类型\n\
- dataStore_list: 列出匹配前缀的所有key，params: {\"prefix\":\"item_\"}\n\
- dataStore_search: 搜索匹配数据，params: {\"prefix\":\"item_\",\"searchFields\":[\"name\",\"desc\"]}\n\
\n\
## 结果格式\n\
每条数据自动附加_key字段(原始存储key)，value如果是JSON会自动解析。\n\
\n\
## 示例\n\
```json\n\
\"queries\": {\n\
  \"all_items\": {\n\
    \"type\": \"dataStore_list\",\n\
    \"params\": {\"prefix\": \"item_\"},\n\
    \"autoRefresh\": true\n\
  },\n\
  \"search_items\": {\n\
    \"type\": \"dataStore_search\",\n\
    \"params\": {\"prefix\": \"item_\", \"searchFields\": [\"name\", \"description\"]},\n\
    \"autoRefresh\": true\n\
  }\n\
}\n\
```\n\
\n\
## 数据约定\n\
- key用前缀分组: item_xxx, note_xxx\n\
- value为JSON字符串: {\"name\":\"xxx\",\"category\":\"yyy\"}\n\
- 删除通过_key字段定位\n\
";

var CONFIG_DOC = "\
# 配置字段 (config)\n\
\n\
## 字段类型\n\
- string: 文本输入\n\
- password: 密码输入(隐藏显示)\n\
- integer: 数字输入\n\
- boolean: 开关\n\
- select: 下拉选择(需配合options)\n\
\n\
## 示例\n\
```json\n\
\"config\": [\n\
  {\"name\": \"api_key\", \"type\": \"password\", \"label\": \"API Key\", \"required\": true, \"placeholder\": \"sk-...\"},\n\
  {\"name\": \"base_url\", \"type\": \"string\", \"label\": \"服务地址\", \"default\": \"https://api.example.com\", \"required\": true},\n\
  {\"name\": \"model\", \"type\": \"select\", \"label\": \"模型\", \"required\": true, \"options\": [{\"value\":\"gpt-4o\",\"label\":\"GPT-4o\"},{\"value\":\"gpt-4o-mini\",\"label\":\"GPT-4o Mini\"}]},\n\
  {\"name\": \"max_results\", \"type\": \"integer\", \"label\": \"最大结果数\", \"default\": 10},\n\
  {\"name\": \"auto_save\", \"type\": \"boolean\", \"label\": \"自动保存\", \"default\": true}\n\
]\n\
```\n\
\n\
## JS中访问\n\
```javascript\n\
var apiKey = config.api_key;\n\
var baseUrl = config.base_url;\n\
var max = config.max_results || 10;\n\
```\n\
";

var PROMPT_DOC = "\
# 提示词模板 (promptTemplate)\n\
\n\
注入系统提示词，让AI知晓可用工具和调用时机。\n\
\n\
## 示例\n\
```json\n\
\"promptTemplate\": \"你拥有天气查询能力。可用工具：\\n1. get_weather(city) - 查询天气\\n2. get_forecast(city,days) - 天气预报\\n当用户询问天气时主动调用。\"\n\
```\n\
\n\
## 编写要点\n\
1. 说明AI拥有什么能力\n\
2. 列出可用工具及调用方式\n\
3. 说明何时应该主动调用\n\
4. 保持简洁，避免过长\n\
";

var FULL_SPEC = QUICKSTART + "\n\n---\n\n" + MANIFEST_DOC + "\n\n---\n\n" + MAINJS_DOC + "\n\n---\n\n" + SANDBOX_DOC + "\n\n---\n\n" + UI_DOC + "\n\n---\n\n" + UI_COMPONENTS_DOC + "\n\n---\n\n" + UI_ACTIONS_DOC + "\n\n---\n\n" + UI_QUERIES_DOC + "\n\n---\n\n" + CONFIG_DOC + "\n\n---\n\n" + PROMPT_DOC;

exports.get_plugin_docs = get_plugin_docs;