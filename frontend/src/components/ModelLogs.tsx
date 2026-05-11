import React, { useState, useEffect } from 'react';
import { Search, ChevronLeft, ChevronRight, Loader2, Eye, Calendar, Cpu, Link2, FileJson, Info, X, Clock } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

export function ModelLogs() {
  const [logs, setLogs] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(1);
  const [limit] = useState(10);
  const [selectedLog, setSelectedLog] = useState<any>(null);
  const [jsonView, setJsonView] = useState<{data: any, title: string} | null>(null);
  const [isJsonFullScreen, setIsJsonFullScreen] = useState(false);
  const [copyStatus, setCopyStatus] = useState(false);

  const fetchLogs = async () => {
    setLoading(true);
    try {
      const skip = (page - 1) * limit;
      const res = await fetch(`http://localhost:8888/model-logs/?search=${encodeURIComponent(search)}&skip=${skip}&limit=${limit}`);
      const data = await res.json();
      setLogs(data.logs);
      setTotal(data.total);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs();
  }, [page, limit]);

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setPage(1);
    fetchLogs();
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
          onClick={() => { setPage(1); fetchLogs(); }}
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
                <th className="px-6 py-4 text-xs font-bold uppercase tracking-wider text-muted-foreground text-right">详情</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center">
                    <Loader2 className="w-8 h-8 animate-spin mx-auto text-primary/50 mb-2" />
                    <span className="text-sm text-muted-foreground">加载中...</span>
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-6 py-12 text-center text-sm text-muted-foreground">
                    未找到匹配的调用记录
                  </td>
                </tr>
              ) : (
                logs.map((log) => {
                  const duration = log.ended_at ? (new Date(log.ended_at).getTime() - new Date(log.started_at).getTime()) / 1000 : 0;
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
                          log.status_code === '200' ? "bg-emerald-500/10 text-emerald-500" : "bg-red-500/10 text-red-500"
                        )}>
                          {log.status_code || 'ERR'}
                        </span>
                      </td>
                      <td className="px-6 py-4">
                        <div className="text-xs">{new Date(log.started_at).toLocaleString()}</div>
                      </td>
                      <td className="px-6 py-4">
                        <div className="flex items-center gap-1 text-xs font-medium text-primary">
                          <Clock className="w-3 h-3" />
                          {duration.toFixed(2)}s
                        </div>
                      </td>
                      <td className="px-6 py-4 text-right">
                        <button 
                          onClick={() => setSelectedLog(log)}
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

                <div className="space-y-4">
                  <div className="relative group">
                    <div className="flex items-center justify-between mb-2">
                      <h4 className="text-xs font-bold text-blue-400 flex items-center gap-2 uppercase tracking-widest">
                        <FileJson className="w-3.5 h-3.5" />
                        Request Payload (模型入参)
                      </h4>
                      <button 
                        onClick={() => setJsonView({data: selectedLog.request_payload, title: "模型入参 (Request Payload)"})}
                        className="p-1 hover:bg-blue-500/20 rounded text-[10px] text-blue-400 flex items-center gap-1 transition-colors"
                      >
                        <Eye className="w-3 h-3" /> 全屏查看
                      </button>
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
              }}
            >
              <div 
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
                <pre className="text-sm font-mono text-blue-100/90 leading-relaxed">
                  {JSON.stringify(jsonView.data, null, 2)}
                </pre>
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
