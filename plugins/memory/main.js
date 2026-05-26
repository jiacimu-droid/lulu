// 记忆库插件 - 通过原生桥接调用记忆库服务
// __memoryBankBridge 由原生代码注入，提供记忆库的核心操作

function memory_recall(params) {
  var query = params.query || "";
  var count = params.count || config.recall_count || 5;
  
  if (!query) {
    return { success: false, error: "query is required" };
  }
  
  if (!__memoryBankBridge) {
    return { success: false, error: "记忆库桥接未初始化，请检查插件配置" };
  }
  
  var resultJson = __memoryBankBridge("recall", JSON.stringify({
    query: query,
    count: count
  }));
  
  var result = JSON.parse(resultJson);
  return result;
}

function memory_save(params) {
  var content = params.content || "";
  
  if (!content) {
    return { success: false, error: "content is required" };
  }
  
  if (!__memoryBankBridge) {
    return { success: false, error: "记忆库桥接未初始化，请检查插件配置" };
  }
  
  var resultJson = __memoryBankBridge("save", JSON.stringify({
    content: content,
    type: "manual"
  }));
  
  var result = JSON.parse(resultJson);
  return result;
}

function memory_search(params) {
  var keyword = params.keyword || "";
  var type = params.type || "";
  var limit = params.limit || 20;
  
  if (!__memoryBankBridge) {
    return { success: false, error: "记忆库桥接未初始化，请检查插件配置" };
  }
  
  var resultJson = __memoryBankBridge("search", JSON.stringify({
    keyword: keyword,
    type: type,
    limit: limit
  }));
  
  var result = JSON.parse(resultJson);
  return result;
}

function memory_delete(params) {
  var id = params.id;
  
  if (!id) {
    return { success: false, error: "id is required" };
  }
  
  if (!__memoryBankBridge) {
    return { success: false, error: "记忆库桥接未初始化，请检查插件配置" };
  }
  
  var resultJson = __memoryBankBridge("delete", JSON.stringify({
    id: id
  }));
  
  var result = JSON.parse(resultJson);
  return result;
}

exports.memory_recall = memory_recall;
exports.memory_save = memory_save;
exports.memory_search = memory_search;
exports.memory_delete = memory_delete;