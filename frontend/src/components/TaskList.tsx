import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, RefreshCw, Trash2, Video, Scan } from 'lucide-react';
import { cn } from '@/lib/utils';
import { TaskCreation } from './TaskCreation';
type StatusFilter = 'ALL' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export function TaskList() {
  const navigate = useNavigate();
  const [showCreation, setShowCreation] = useState(false);
  const [tasks, setTasks] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [page, setPage] = useState(1);
  const [totalCounts, setTotalCounts] = useState<any>({ ALL: 0, PROCESSING: 0, COMPLETED: 0, FAILED: 0 });
  const pageSize = 10;
  const fetchTasks = async () => {
    try {
      const skip = (page - 1) * pageSize;
      const res = await fetch(`http://localhost:8000/tasks/?status=${statusFilter}&skip=${skip}&limit=${pageSize}`);
      if (res.ok) {
        const data = await res.json();
        setTasks(data.tasks || []);
        setTotalCounts(data.total_counts || { ALL: 0, PROCESSING: 0, COMPLETED: 0, FAILED: 0 });
      }
    } catch (err) {
      console.error("Fetch tasks failed", err);
    } finally {
      setLoading(false);
    }
  };
  const handleRefresh = async () => {
    setRefreshing(true);
    await fetchTasks();
    setTimeout(() => setRefreshing(false), 600);
  };
  const handleDelete = async (id: string) => {
    if (!window.confirm("确定要删除该任务吗？删除后文件将无法找回。")) return;
    try {
      const res = await fetch(`http://localhost:8000/tasks/${id}`, { method: 'DELETE' });
      if (res.ok) {
        fetchTasks();
      }
    } catch (err) {
      console.error("Delete task failed", err);
    }
  };
  useEffect(() => {
    fetchTasks();
    const interval = setInterval(fetchTasks, 5000);
    return () => clearInterval(interval);
  }, [page, statusFilter]);
  const formatTime = (ts: string) => {
    if (!ts) return "--";
    return new Date(ts).toLocaleString();
  };
  const tabs: { label: string; value: StatusFilter; count?: number }[] = [
    { label: '全部任务', value: 'ALL', count: totalCounts.ALL },
    { label: '处理中', value: 'PROCESSING', count: totalCounts.PROCESSING },
    { label: '已完成', value: 'COMPLETED', count: totalCounts.COMPLETED },
    { label: '失败', value: 'FAILED', count: totalCounts.FAILED },
  ];
  return (
    <>
      <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col">
        <div className="flex justify-between items-center mb-6 shrink-0">
          <h3 className="text-lg font-medium flex items-center gap-3">
            任务管理
            <button onClick={handleRefresh} className="p-1 hover:bg-muted rounded-full cursor-pointer transition-colors" title="刷新列表">
              <RefreshCw className={cn("w-4 h-4 text-muted-foreground", refreshing && "animate-spin")} />
            </button>
          </h3>
          <button
            onClick={() => setShowCreation(true)}
            className="bg-primary text-primary-foreground px-4 py-2 rounded-md text-sm flex items-center gap-2 cursor-pointer hover:bg-primary/90 transition-colors"
          >
            + 新建任务
          </button>
        </div>
        <div className="grid grid-cols-4 gap-4 mb-6 shrink-0">
          {tabs.map(tab => (
            <div
              key={tab.value}
              onClick={() => { setStatusFilter(tab.value); setPage(1); }}
              className={cn(
                "p-4 rounded-xl border transition-all cursor-pointer flex flex-col gap-1",
                statusFilter === tab.value
                  ? "bg-primary/10 border-primary shadow-sm"
                  : "bg-muted/30 border-border hover:border-primary/30 hover:bg-muted/50"
              )}
            >
              <div className="text-xs text-muted-foreground font-medium">{tab.label}</div>
              <div className={cn(
                "text-2xl font-bold",
                statusFilter === tab.value ? "text-primary" : "text-foreground"
              )}>
                {tab.count ?? 0}
              </div>
            </div>
          ))}
        </div>
        <div className="flex-1 overflow-y-auto custom-scrollbar">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20 sticky top-0">
              <tr>
                <th className="px-4 py-3 rounded-l-lg font-medium">任务名称 / ID</th>
                <th className="px-4 py-3 font-medium">识别方式</th>
                <th className="px-4 py-3 font-medium">分析模型</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">相似度</th>
                <th className="px-4 py-3 font-medium">创建时间</th>
                <th className="px-4 py-3 rounded-r-lg font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {tasks.length === 0 ? (
                <tr><td colSpan={7} className="text-center py-8 text-muted-foreground">暂无任务数据</td></tr>
              ) : tasks.map((task, i) => (
                <tr key={i} className="border-b border-border/50 hover:bg-muted/20 transition-colors">
                  <td className="px-4 py-4 font-medium text-foreground">
                    <div className="flex flex-col">
                      <span>{task.task_name}</span>
                      <span className="text-[10px] text-muted-foreground font-normal">{task.id.split('-')[0]}</span>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <div className="flex items-center gap-2">
                      {task.preprocess_options?.recognition_mode === 'video' ? (
                        <Video className="w-3.5 h-3.5 text-blue-500" />
                      ) : (
                        <Scan className="w-3.5 h-3.5 text-purple-500" />
                      )}
                      <span className={cn(
                        "text-[10px] px-2 py-0.5 rounded-full border font-medium",
                        task.preprocess_options?.recognition_mode === 'video'
                          ? "bg-blue-500/10 text-blue-500 border-blue-500/20"
                          : "bg-purple-500/10 text-purple-500 border-purple-500/20"
                      )}>
                        {task.preprocess_options?.recognition_mode === 'video' ? '视频 LLM 识别' : '图片 LLM 识别'}
                      </span>
                    </div>
                  </td>
                  <td className="px-4 py-4">
                    <span className="text-xs bg-muted px-2 py-0.5 rounded text-muted-foreground border border-border/50">
                      {task.model_id}
                    </span>
                  </td>
                  <td className="px-4 py-4">
                    <span className={cn(
                      "px-2.5 py-1 text-xs rounded-full border",
                      task.status === 'COMPLETED' ? "bg-emerald-500/10 text-emerald-500 border-emerald-500/20" :
                        task.status === 'PROCESSING' ? "bg-amber-500/10 text-amber-500 border-amber-500/20" :
                          task.status === 'FAILED' ? "bg-red-500/10 text-red-500 border-red-500/20" :
                            "bg-blue-500/10 text-blue-500 border-blue-500/20"
                    )}>
                      {task.status === 'COMPLETED' ? '已完成' : task.status === 'PROCESSING' ? '处理中' : task.status === 'FAILED' ? '失败' : '排队中'}
                    </span>
                  </td>
                  <td className="px-4 py-4 font-medium">{task.similarity_score ? `${task.similarity_score.toFixed(1)}%` : '--'}</td>
                  <td className="px-4 py-4 text-muted-foreground whitespace-nowrap">{formatTime(task.created_at)}</td>
                  <td className="px-4 py-4">
                    <div className="flex items-center gap-4">
                      <button
                        onClick={() => navigate(`/tasks/${task.id}`)}
                        className={cn(
                          "flex items-center gap-1 cursor-pointer transition-colors font-medium",
                          (task.status === 'COMPLETED' || task.status === 'FAILED') ? "text-primary hover:text-primary/80" : "text-muted-foreground cursor-not-allowed opacity-50"
                        )}
                        disabled={task.status !== 'COMPLETED' && task.status !== 'FAILED'}
                      >
                        <Eye className="w-4 h-4" />
                        查看
                      </button>
                      <button
                        onClick={() => handleDelete(task.id)}
                        className="text-muted-foreground hover:text-red-500 transition-colors cursor-pointer"
                        title="删除任务"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="mt-4 flex items-center justify-between border-t border-border pt-4 shrink-0">
          <div className="text-xs text-muted-foreground">
            显示第 {totalCounts[statusFilter] > 0 ? ((page - 1) * pageSize) + 1 : 0} 到 {Math.min(page * pageSize, totalCounts[statusFilter])} 条任务，共 {totalCounts[statusFilter]} 条
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setPage(p => Math.max(1, p - 1))}
              disabled={page === 1}
              className="px-3 py-1 text-xs border border-border rounded hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              上一页
            </button>
            <div className="flex items-center px-4 text-xs font-medium">
              第 {page} 页
            </div>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page * pageSize >= totalCounts[statusFilter]}
              className="px-3 py-1 text-xs border border-border rounded hover:bg-muted disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              下一页
            </button>
          </div>
        </div>
      </div>
      {showCreation && <TaskCreation onClose={() => setShowCreation(false)} onTaskCreated={fetchTasks} />}
    </>
  );
}
