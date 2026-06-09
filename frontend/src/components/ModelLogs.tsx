import { useState, useEffect } from 'react';
import { Search, ChevronLeft, ChevronRight, ChevronDown, Loader2, Eye, Cpu, Link2, FileJson, X, Clock, Image as ImageIcon, ZoomIn, ZoomOut, RotateCcw } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence, useDragControls } from 'framer-motion';

export function ModelLogs() {
  const [logs, setLogs] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [limit] = useState(10);
  const [selectedLog, setSelectedLog] = useState<any>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [jsonView, setJsonView] = useState<{data: any, title: string} | null>(null);
  const [imageView, setImageView] = useState<{images: string[], title: string} | null>(null);
  const [focusedImgIdx, setFocusedImgIdx] = useState(0);
  const [zoom, setZoom] = useState(1);
  const [isJsonFullScreen, setIsJsonFullScreen] = useState(false);
  const [copyStatus, setCopyStatus] = useState(false);
  const controls = useDragControls();

  // 自动尝试递归解析 JSON 字符串，支持转义嵌套、Markdown 代码块以及字符串中嵌入的 JSON 块
  const autoParseJson = (data: any): any => {
    if (!data) return data;
    
    if (typeof data === 'string') {
      let s = data.trim();
      
      // 1. 处理可能的 Markdown 代码块包裹 (常见的 LLM 输出格式)
      if (s.includes('```')) {
        const mdJsonRegex = /```(?:json)?\s*([\s\S]*?)\s*```/i;
        const match = s.match(mdJsonRegex);
        if (match && match[1]) {
          s = match[1].trim();
        }
      }

      // 2. 尝试全量解析标准的 JSON 对象或数组
      if ((s.startsWith('{') && s.endsWith('}')) || (s.startsWith('[') && s.endsWith(']'))) {
        try {
          const parsed = JSON.parse(s);
          return autoParseJson(parsed);
        } catch {
          // 继续尝试下方的“智能提取”逻辑
        }
      }

      // 3. 智能提取：如果字符串中包含 JSON 块 (即使不是全量 JSON)
      // 常见于 Prompt 中包含的示例或指令
      const extractJsonBlocks = (text: string) => {
        const blocks: {start: number, end: number, parsed: any}[] = [];
        let pos = 0;
        while (pos < text.length) {
          const start = text.indexOf('{', pos);
          const startArr = text.indexOf('[', pos);
          const actualStart = (start !== -1 && (startArr === -1 || start < startArr)) ? start : startArr;
          
          if (actualStart === -1) break;
          
          let depth = 0;
          let end = -1;
          const openChar = text[actualStart];
          const closeChar = openChar === '{' ? '}' : ']';

          for (let i = actualStart; i < text.length; i++) {
            if (text[i] === openChar) depth++;
            else if (text[i] === closeChar) {
              depth--;
              if (depth === 0) {
                end = i;
                break;
              }
            }
          }
          
          if (end !== -1) {
            const candidate = text.substring(actualStart, end + 1);
            try {
              // 验证是否为合法 JSON
              const parsed = JSON.parse(candidate);
              // 过滤掉太简单的内容（如只有数字或简单的空对象，除非它确实是 JSON）
              if (candidate.length > 4) {
                blocks.push({ start: actualStart, end, parsed });
                pos = end + 1;
                continue;
              }
            } catch {
              // Continue scanning for embedded JSON.
            }
          }
          pos = actualStart + 1;
        }
        return blocks;
      };

      const foundBlocks = extractJsonBlocks(s);
      if (foundBlocks.length > 0) {
        const result: any[] = [];
        let lastPos = 0;
        foundBlocks.forEach(block => {
          if (block.start > lastPos) {
            const textPart = s.substring(lastPos, block.start).trim();
            if (textPart) result.push(textPart);
          }
          result.push(autoParseJson(block.parsed));
          lastPos = block.end + 1;
        });
        if (lastPos < s.length) {
          const tail = s.substring(lastPos).trim();
          if (tail) result.push(tail);
        }
        return result.length === 1 ? result[0] : result;
      }
      
      // 4. 处理可能被多重转义包裹的单字符串
      if (s.startsWith('"') && s.endsWith('"') && s.length > 2) {
        try {
          const unquoted = JSON.parse(s);
          if (typeof unquoted === 'string' && unquoted !== s) {
            return autoParseJson(unquoted);
          }
        } catch {
          // Not a quoted JSON string.
        }
      }
    } else if (typeof data === 'object' && data !== null) {
      if (Array.isArray(data)) {
        return data.map(item => autoParseJson(item));
      } else {
        const result: any = {};
        for (const key in data) {
          result[key] = autoParseJson(data[key]);
        }
        return result;
      }
    }
    return data;
  };

  // 提取 Payload 中的 Base64 图片
  const extractBase64Images = (data: any): string[] => {
    const images: string[] = [];
    
    const search = (obj: any) => {
      if (!obj) return;
      if (typeof obj === 'string') {
        // 匹配 data:image/xxx;base64,xxx 格式
        if (obj.startsWith('data:image/')) {
          images.push(obj);
        } 
        // 匹配可能是原始 base64 的长字符串 (简单启发式：长度>500且字符集匹配)
        else if (obj.length > 500 && /^[A-Za-z0-9+/=]+$/.test(obj.substring(0, 100))) {
          // 尝试补齐前缀，默认为 jpeg
          images.push(`data:image/jpeg;base64,${obj}`);
        }
      } else if (Array.isArray(obj)) {
        obj.forEach(search);
      } else if (typeof obj === 'object') {
        // 特殊处理 MiniMax 的 images 数组
        if ('images' in obj && Array.isArray(obj.images)) {
            obj.images.forEach((img: any) => {
                if (typeof img === 'string') {
                    if (img.startsWith('data:image/')) images.push(img);
                    else images.push(`data:image/jpeg;base64,${img}`);
                }
            });
        } else {
            Object.values(obj).forEach(search);
        }
      }
    };
    
    search(data);
    // 去重
    return Array.from(new Set(images));
  };

  const fetchLogs = async (showLoading = false) => {
    if (showLoading) setLoading(true);
    try {
      const skip = (page - 1) * limit;
      const res = await fetch(
        `http://localhost:8888/model-logs/?search=${encodeURIComponent(search)}&skip=${skip}&limit=${limit}`,
        { cache: 'no-store' }
      );
      if (!res.ok) throw new Error(`Model logs request failed: ${res.status}`);
      const data = await res.json();
      setLogs(data.logs);
      setTotal(data.total);
    } catch (e) {
      console.error(e);
    } finally {
      if (showLoading) setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs(true);
    const interval = setInterval(() => fetchLogs(false), 5000);
    return () => clearInterval(interval);
  }, [page, limit]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    fetchLogs(true);
  };

  const handleShowDetail = async (log: any) => {
    setSelectedLog(log);
    setLoadingDetail(true);
    try {
      const res = await fetch(`http://localhost:8888/model-logs/${log.id}`);
      if (!res.ok) throw new Error(`Failed to fetch log details: ${res.status}`);
      const detail = await res.json();
      setSelectedLog(detail);
    } catch (e) {
      console.error(e);
    } finally {
      setLoadingDetail(false);
    }
  };

  const totalPages = Math.ceil(total / limit);

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">模型调用记录</h2>
          <p className="text-sm text-muted-foreground">监控并审计所有 VLM 接口的请求与响应详情</p>
        </div>
      </div>

      {/* Filter Bar */}
      <div className="bg-card border border-border rounded-xl p-4 shadow-sm flex items-center gap-4">
        <form onSubmit={handleSearch} className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input 
            type="text" 
            placeholder="搜索任务名称、ID 或模型标识..."
            className="w-full bg-muted/50 border border-border rounded-lg pl-10 pr-4 py-2 text-sm outline-none focus:border-primary transition-colors"
            value={search}
            onChange={e => setSearch(e.target.value)}
          />
        </form>
        <button 
          onClick={() => { setPage(1); fetchLogs(true); }}
          className="bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm font-medium hover:bg-primary/90 transition-colors"
        >
          查询
        </button>
      </div>

      {/* Logs Table */}
      <div className="bg-card border border-border rounded-xl overflow-hidden shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-muted/30 border-b border-border">
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">任务名称 / ID</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">调用模型</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">接口 URL</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">状态</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">开始时间</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">耗时</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground">总消耗Token</th>
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground text-right">详情</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center">
                    <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary/50 mb-2" />
                    <span className="text-sm text-muted-foreground">加载中...</span>
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-6 py-12 text-center text-sm text-muted-foreground">
                    未找到匹配的调用记录
                  </td>
                </tr>
              ) : (
                logs.map((log) => {
                  const isProcessing = log.status_code === 'PROCESSING';
                  const duration = log.started_at && log.ended_at
                    ? (new Date(log.ended_at).getTime() - new Date(log.started_at).getTime()) / 1000
                    : 0;
                  return (
                    <tr key={log.id} className="hover:bg-muted/10 transition-colors group">
                      <td className="px-6 py-4">
                        <div className="font-medium text-sm truncate max-w-[200px]" title={log.task_name}>{log.task_name || '未知任务'}</div>
                        <div className="text-[10px] text-muted-foreground font-mono mt-0.5">{log.task_id}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-2">
                          <Cpu className="w-3.5 h-3.5 text-primary" />
                          <span className="text-sm font-mono">{log.model_id}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1.5 text-xs text-muted-foreground max-w-[400px]" title={log.model_url}>
                          <Link2 className="w-3 h-3 shrink-0" />
                          <span className="truncate">{log.model_url}</span>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <span className={cn(
                          "px-2 py-0.5 rounded-full text-[10px] font-bold",
                          log.status_code === '200'
                            ? "bg-emerald-500/10 text-emerald-500"
                            : isProcessing
                              ? "bg-amber-500/10 text-amber-500"
                              : "bg-red-500/10 text-red-500"
                        )}>
                          {isProcessing ? '调用中' : (log.status_code || 'ERR')}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-xs">{new Date(log.started_at).toLocaleString()}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1 text-xs font-medium text-primary">
                          <Clock className="w-3 h-3" />
                          {isProcessing ? '进行中' : `${duration.toFixed(2)}s`}
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-xs font-mono font-bold text-indigo-400">
                          {(log.input_tokens || 0) + (log.output_tokens || 0)}
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button 
                          onClick={() => handleShowDetail(log)}
                          className="p-2 hover:bg-primary/10 hover:text-primary rounded-lg transition-colors"
                        >
                          <Eye className="w-4 h-4" />
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {total > 0 && (
          <div className="px-6 py-4 border-t border-border flex items-center justify-between bg-muted/20">
            <div className="text-xs text-muted-foreground">
              共 <span className="font-medium text-foreground">{total}</span> 条记录，当前第 {page} / {totalPages} 页
            </div>
            <div className="flex items-center gap-2">
              <button 
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1 || loading}
                className="p-1.5 border border-border rounded-md disabled:opacity-50 hover:bg-muted transition-colors"
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <div className="flex gap-1">
                {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                  let pNum;
                  if (totalPages <= 5) pNum = i + 1;
                  else if (page <= 3) pNum = i + 1;
                  else if (page >= totalPages - 2) pNum = totalPages - 4 + i;
                  else pNum = page - 2 + i;
                  
                  return (
                    <button 
                      key={pNum}
                      onClick={() => setPage(pNum)}
                      className={cn(
                        "w-8 h-8 rounded-md text-xs font-medium transition-colors",
                        page === pNum ? "bg-primary text-primary-foreground" : "hover:bg-muted"
                      )}
                    >
                      {pNum}
                    </button>
                  );
                })}
              </div>
              <button 
                onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                disabled={page === totalPages || loading}
                className="p-1.5 border border-border rounded-md disabled:opacity-50 hover:bg-muted transition-colors"
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Log Detail Modal */}
      <AnimatePresence>
        {selectedLog && (
          <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-[100] flex items-center justify-center p-4" onClick={() => setSelectedLog(null)}>
            <motion.div 
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              className="bg-card border border-border rounded-2xl shadow-2xl w-full max-w-4xl h-[80vh] flex flex-col overflow-hidden"
              onClick={e => e.stopPropagation()}
            >
              <div className="px-6 py-4 border-b border-border flex justify-between items-center bg-muted/30">
                <div>
                  <h3 className="text-lg font-bold">调用记录详情</h3>
                  <p className="text-[10px] text-muted-foreground font-mono">LOG_ID: {selectedLog.id}</p>
                </div>
                <button onClick={() => setSelectedLog(null)} className="p-2 hover:bg-muted rounded-full">
                  <X className="w-5 h-5" />
                </button>
              </div>

              <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
                <div className="grid grid-cols-3 gap-4">
                  <DetailItem label="任务名称" val={selectedLog.task_name} />
                  <DetailItem label="关联任务 ID" val={selectedLog.task_id} mono />
                  <DetailItem label="请求 URL" val={selectedLog.model_url} mono />
                  <DetailItem label="模型标识" val={selectedLog.model_id} mono />
                  <DetailItem label="状态码" val={selectedLog.status_code} />
                  <DetailItem label="开始时间" val={new Date(selectedLog.started_at).toLocaleString()} />
                  <DetailItem label="输入 Token" val={selectedLog.input_tokens?.toString()} />
                  <DetailItem label="输出 Token" val={selectedLog.output_tokens?.toString()} />
                  <DetailItem label="总耗时" val={`${((new Date(selectedLog.ended_at).getTime() - new Date(selectedLog.started_at).getTime()) / 1000).toFixed(2)}s`} />
                </div>

                {loadingDetail ? (
                  <div className="flex flex-col items-center justify-center py-12 border border-border border-dashed rounded-xl bg-muted/20">
                    <Loader2 className="w-8 h-8 animate-spin text-primary/50 mb-2" />
                    <span className="text-sm text-muted-foreground font-medium">正在加载接口出入参明细...</span>
                  </div>
                ) : (
                  <div className="space-y-4">
                    <div className="relative group">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-xs font-bold text-blue-400 flex items-center gap-2 uppercase tracking-widest">
                          <FileJson className="w-3.5 h-3.5" />
                          Request Payload (模型入参)
                        </h4>
                        <div className="flex items-center gap-2">
                          {selectedLog.request_payload && extractBase64Images(selectedLog.request_payload).length > 0 && (
                            <button 
                              onClick={() => setImageView({
                                images: extractBase64Images(selectedLog.request_payload), 
                                title: "入参图片 (Input Images)"
                              })}
                              className="p-1 hover:bg-indigo-500/20 rounded text-[10px] text-indigo-400 flex items-center gap-1 transition-colors"
                            >
                              <ImageIcon className="w-3 h-3" /> 查看入参图片
                            </button>
                          )}
                          <button 
                            onClick={() => setJsonView({data: selectedLog.request_payload, title: "模型入参 (Request Payload)"})}
                            className="p-1 hover:bg-blue-500/20 rounded text-[10px] text-blue-400 flex items-center gap-1 transition-colors"
                          >
                            <Eye className="w-3 h-3" /> 全屏查看
                          </button>
                        </div>
                      </div>
                      <pre className="bg-muted/50 p-4 rounded-lg text-[11px] font-mono overflow-x-auto border border-border/50 max-h-64 whitespace-pre-wrap custom-scrollbar">
                        {JSON.stringify(selectedLog.request_payload, null, 2)}
                      </pre>
                    </div>

                    <div className="relative group">
                      <div className="flex items-center justify-between mb-2">
                        <h4 className="text-xs font-bold text-emerald-500 flex items-center gap-2 uppercase tracking-widest">
                          <FileJson className="w-3.5 h-3.5" />
                          Response Body (模型出参)
                        </h4>
                        <button 
                          onClick={() => setJsonView({data: selectedLog.response_body, title: "模型出参 (Response Body)"})}
                          className="p-1 hover:bg-emerald-500/20 rounded text-[10px] text-emerald-500 flex items-center gap-1 transition-colors"
                        >
                          <Eye className="w-3 h-3" /> 全屏查看
                        </button>
                      </div>
                      <pre className="bg-muted/50 p-4 rounded-lg text-[11px] font-mono overflow-x-auto border border-border/50 max-h-64 whitespace-pre-wrap custom-scrollbar">
                        {JSON.stringify(selectedLog.response_body, null, 2)}
                      </pre>
                    </div>
                  </div>
                )}
              </div>

              <div className="px-6 py-4 border-t border-border bg-muted/10 flex justify-end">
                <button 
                  onClick={() => setSelectedLog(null)}
                  className="bg-primary text-primary-foreground px-6 py-2 rounded-lg text-sm font-medium"
                >
                  关闭
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* JSON 独立查看弹窗 (支持拖拽与全屏) */}
      <AnimatePresence>
        {jsonView && (
          <div className="fixed inset-0 z-[1001] flex items-center justify-center">
            <motion.div 
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="absolute inset-0 bg-black/40 backdrop-blur-sm"
              onClick={() => { setJsonView(null); setIsJsonFullScreen(false); }}
            />
            <motion.div
              layout
              drag={!isJsonFullScreen}
              dragControls={controls}
              dragListener={false}
              dragMomentum={false}
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ 
                x: isJsonFullScreen ? 0 : undefined,
                y: isJsonFullScreen ? 0 : undefined,
                scale: 1, 
                opacity: 1,
                width: isJsonFullScreen ? "100vw" : "896px", 
                height: isJsonFullScreen ? "100vh" : "80vh",
                borderRadius: isJsonFullScreen ? "0px" : "16px",
              }}
              exit={{ scale: 0.95, opacity: 0 }}
              transition={{ type: "spring", damping: 30, stiffness: 300 }}
              className={cn(
                "bg-[#1a1f2e] border border-white/10 shadow-2xl flex flex-col pointer-events-auto overflow-hidden",
                isJsonFullScreen ? "fixed inset-0 z-[1002]" : "relative z-[1001]",
                !isJsonFullScreen && "resize"
              )}
              style={{
                maxWidth: isJsonFullScreen ? "100vw" : "95%",
                maxHeight: isJsonFullScreen ? "100vh" : "90%",
                touchAction: "none"
              }}
            >
              <div 
                onPointerDown={(e) => !isJsonFullScreen && controls.start(e)}
                className={cn(
                  "px-6 py-4 border-b border-white/5 flex items-center justify-between bg-white/5 select-none",
                  !isJsonFullScreen ? "cursor-move" : "cursor-default"
                )}
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 bg-blue-500/10 rounded-lg">
                    <FileJson className="w-5 h-5 text-blue-400" />
                  </div>
                  <h3 className="font-bold text-white tracking-tight">{jsonView.title}</h3>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(JSON.stringify(jsonView.data, null, 2));
                      setCopyStatus(true);
                      setTimeout(() => setCopyStatus(false), 2000);
                    }}
                    className="px-3 py-1.5 text-xs bg-white/5 hover:bg-white/10 rounded-lg border border-white/10 text-white/70 transition-all"
                  >
                    {copyStatus ? "已复制" : "复制 JSON"}
                  </button>
                  <button
                    onClick={() => setIsJsonFullScreen(!isJsonFullScreen)}
                    className="p-2 hover:bg-white/5 rounded-full text-white/40 hover:text-white transition-colors"
                    title={isJsonFullScreen ? "退出全屏" : "全屏"}
                  >
                    <motion.div animate={{ rotate: isJsonFullScreen ? 180 : 0 }}>
                      {isJsonFullScreen ? <ChevronLeft className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                    </motion.div>
                  </button>
                  <button 
                    onClick={() => { setJsonView(null); setIsJsonFullScreen(false); }}
                    className="p-2 hover:bg-white/5 rounded-full text-white/40 hover:text-white transition-colors"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>
              <div className="p-6 overflow-auto custom-scrollbar flex-1 bg-black/20">
                <div className="text-sm font-mono leading-relaxed p-2">
                  <JsonView data={autoParseJson(jsonView.data)} />
                </div>
              </div>
              
              {!isJsonFullScreen && (
                <div className="absolute bottom-1 right-1 w-4 h-4 cursor-se-resize flex items-end justify-end opacity-20 hover:opacity-100 transition-opacity">
                  <div className="w-2 h-0.5 bg-white rotate-[-45deg] translate-x-1" />
                  <div className="w-3 h-0.5 bg-white rotate-[-45deg]" />
                </div>
              )}
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* 图片查看弹窗 (增强版：支持缩放、拖拽与多图切换) */}
      <AnimatePresence>
        {imageView && (
          <div className="fixed inset-0 bg-black/95 backdrop-blur-xl z-[2000] flex flex-col overflow-hidden" onClick={() => setImageView(null)}>
            {/* 顶部控制栏 */}
            <motion.div 
              initial={{ y: -20, opacity: 0 }}
              animate={{ y: 0, opacity: 1 }}
              className="px-6 py-4 flex items-center justify-between bg-white/5 border-b border-white/10 z-[2001]"
              onClick={e => e.stopPropagation()}
            >
              <div className="flex items-center gap-4">
                <div className="p-2 bg-indigo-500/20 rounded-lg">
                  <ImageIcon className="w-5 h-5 text-indigo-400" />
                </div>
                <div>
                  <h3 className="text-white font-bold tracking-tight">{imageView.title}</h3>
                  <p className="text-[10px] text-white/40 font-mono">IMAGE {focusedImgIdx + 1} / {imageView.images.length} • {Math.round(zoom * 100)}% ZOOM</p>
                </div>
              </div>

              <div className="flex items-center gap-3">
                <div className="flex items-center bg-white/5 rounded-lg border border-white/10 p-1">
                  <button 
                    onClick={() => setZoom(prev => Math.max(0.5, prev - 0.25))}
                    className="p-1.5 hover:bg-white/10 rounded text-white/60 hover:text-white transition-colors"
                    title="缩小"
                  >
                    <ZoomOut className="w-4 h-4" />
                  </button>
                  <div className="px-2 text-[10px] font-mono text-white/40 w-12 text-center">{Math.round(zoom * 100)}%</div>
                  <button 
                    onClick={() => setZoom(prev => Math.min(5, prev + 0.25))}
                    className="p-1.5 hover:bg-white/10 rounded text-white/60 hover:text-white transition-colors"
                    title="放大"
                  >
                    <ZoomIn className="w-4 h-4" />
                  </button>
                  <div className="w-px h-4 bg-white/10 mx-1" />
                  <button 
                    onClick={() => { setZoom(1); }}
                    className="p-1.5 hover:bg-white/10 rounded text-white/60 hover:text-white transition-colors"
                    title="重置"
                  >
                    <RotateCcw className="w-4 h-4" />
                  </button>
                </div>
                
                <button 
                  onClick={() => setImageView(null)}
                  className="p-2 bg-white/10 hover:bg-red-500/20 text-white/60 hover:text-red-400 rounded-lg transition-all"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </motion.div>

            {/* 主展示区 */}
            <div className="flex-1 relative flex items-center justify-center overflow-hidden cursor-grab active:cursor-grabbing">
              <AnimatePresence mode="wait">
                <motion.div
                  key={focusedImgIdx + zoom}
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  exit={{ opacity: 0, scale: 1.1 }}
                  className="w-full h-full flex items-center justify-center p-12"
                >
                  <motion.div
                    drag
                    dragConstraints={{ left: -1000, right: 1000, top: -1000, bottom: 1000 }}
                    dragElastic={0.1}
                    dragMomentum={false}
                    className="relative"
                    style={{ scale: zoom }}
                    onClick={e => e.stopPropagation()}
                  >
                    <img 
                      src={imageView.images[focusedImgIdx]} 
                      alt="Focused Preview" 
                      className="max-w-[90%] max-h-[90%] object-contain shadow-[0_0_50px_rgba(0,0,0,0.5)] rounded-lg border border-white/10 pointer-events-none select-none"
                    />
                  </motion.div>
                </motion.div>
              </AnimatePresence>

              {/* 左右切换按钮 */}
              {imageView.images.length > 1 && (
                <>
                  <button 
                    onClick={(e) => { e.stopPropagation(); setFocusedImgIdx(prev => (prev > 0 ? prev - 1 : imageView.images.length - 1)); setZoom(1); }}
                    className="absolute left-6 top-1/2 -translate-y-1/2 p-4 bg-white/5 hover:bg-white/10 border border-white/10 rounded-full text-white/40 hover:text-white transition-all backdrop-blur-md"
                  >
                    <ChevronLeft className="w-8 h-8" />
                  </button>
                  <button 
                    onClick={(e) => { e.stopPropagation(); setFocusedImgIdx(prev => (prev < imageView.images.length - 1 ? prev + 1 : 0)); setZoom(1); }}
                    className="absolute right-6 top-1/2 -translate-y-1/2 p-4 bg-white/5 hover:bg-white/10 border border-white/10 rounded-full text-white/40 hover:text-white transition-all backdrop-blur-md"
                  >
                    <ChevronRight className="w-8 h-8" />
                  </button>
                </>
              )}
            </div>

            {/* 底部缩略图 */}
            {imageView.images.length > 1 && (
              <motion.div 
                initial={{ y: 20, opacity: 0 }}
                animate={{ y: 0, opacity: 1 }}
                className="px-6 py-6 bg-black/40 border-t border-white/10 flex justify-center gap-4 z-[2001]"
                onClick={e => e.stopPropagation()}
              >
                {imageView.images.map((src, idx) => (
                  <button 
                    key={idx}
                    onClick={() => { setFocusedImgIdx(idx); setZoom(1); }}
                    className={cn(
                      "w-20 h-12 rounded-lg overflow-hidden border-2 transition-all shrink-0",
                      focusedImgIdx === idx ? "border-indigo-500 scale-110 shadow-lg shadow-indigo-500/20" : "border-white/10 grayscale hover:grayscale-0 hover:border-white/40"
                    )}
                  >
                    <img src={src} className="w-full h-full object-cover" alt={`Thumb ${idx}`} />
                  </button>
                ))}
              </motion.div>
            )}
            
            {/* 快捷提示 */}
            <div className="absolute bottom-4 right-6 text-[10px] text-white/20 font-mono pointer-events-none">
              DRAG TO PAN • SCROLL NOT SUPPORTED YET
            </div>
          </div>
        )}
      </AnimatePresence>
    </div>
  );
}

function JsonView({ data, isLast = true }: { data: any, isLast?: boolean }) {
  const [collapsed, setCollapsed] = useState(false);

  if (data === null) return <span className="text-gray-500">null{!isLast && ","}</span>;
  if (typeof data === "boolean") return <span className="text-purple-400">{data.toString()}{!isLast && ","}</span>;
  if (typeof data === "number") return <span className="text-amber-400">{data}{!isLast && ","}</span>;
  if (typeof data === "string") {
    return (
      <span className="text-emerald-400 break-words whitespace-pre-wrap">
        "{data}"{!isLast && ","}
      </span>
    );
  }

  const isArray = Array.isArray(data);
  const keys = isArray ? data : Object.keys(data);
  const isEmpty = isArray ? data.length === 0 : keys.length === 0;

  if (isEmpty) return <span className="text-blue-300/80">{isArray ? "[]" : "{}"}{!isLast && ","}</span>;

  return (
    <div className="inline-block align-top">
      <div 
        className="cursor-pointer hover:bg-white/5 px-1 -ml-1 rounded transition-colors inline-flex items-center gap-1 select-none"
        onClick={(e) => { e.stopPropagation(); setCollapsed(!collapsed); }}
      >
        {collapsed ? <ChevronRight className="w-3.5 h-3.5 text-white/40" /> : <ChevronDown className="w-3.5 h-3.5 text-white/40" />}
        <span className="text-blue-300/80 font-bold">{isArray ? "[" : "{"}</span>
        {collapsed && (
          <span className="px-1.5 py-0.5 bg-white/5 rounded text-[10px] text-white/40 font-sans mx-1">
             {isArray ? `${data.length} items` : `${keys.length} keys`}
          </span>
        )}
      </div>

      {!collapsed && (
        <div className="pl-6 border-l border-white/5 my-0.5 ml-1.5">
          {isArray ? (
            data.map((item: any, i: number) => (
              <div key={i} className="py-0.5">
                <JsonView data={item} isLast={i === data.length - 1} />
              </div>
            ))
          ) : (
            Object.keys(data).map((key, i, arr) => (
              <div key={key} className="flex gap-2 py-0.5">
                <span className="text-blue-400/90 font-medium shrink-0">"{key}":</span>
                <JsonView data={data[key]} isLast={i === arr.length - 1} />
              </div>
            ))
          )}
        </div>
      )}
      
      {!collapsed ? (
        <div className="text-blue-300/80 font-bold">{isArray ? "]" : "}"}{!isLast && ","}</div>
      ) : (
        <span className="text-blue-300/80 font-bold">{isArray ? "]" : "}"}{!isLast && ","}</span>
      )}
    </div>
  );
}

function DetailItem({ label, val, mono = false }: { label: string, val: string, mono?: boolean }) {
  return (
    <div className="space-y-1">
      <div className="text-[10px] text-muted-foreground uppercase font-bold tracking-wider">{label}</div>
      <div className={cn("text-xs truncate", mono ? "font-mono bg-muted/50 px-1.5 py-0.5 rounded border border-border/30" : "font-medium")}>
        {val || '--'}
      </div>
    </div>
  );
}
