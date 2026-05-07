import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, RefreshCw } from 'lucide-react';
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

  const fetchTasks = async () => {
    try {
      const res = await fetch("http://localhost:8000/tasks/");
      if (res.ok) {
        const data = await res.json();
        setTasks(data);
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

  useEffect(() => {
    fetchTasks();
    const interval = setInterval(fetchTasks, 5000);
    return () => clearInterval(interval);
  }, []);

  const formatTime = (ts: string) => {
    if (!ts) return "--";
    return new Date(ts).toLocaleString();
  };

  const filteredTasks = statusFilter === 'ALL' ? tasks : tasks.filter(t => t.status === statusFilter);

  const tabs: { label: string; value: StatusFilter; count?: number }[] = [
    { label: '全部任务', value: 'ALL', count: tasks.length },
    { label: '处理中', value: 'PROCESSING', count: tasks.filter(t => t.status === 'PROCESSING' || t.status === 'PENDING').length },
    { label: '已完成', value: 'COMPLETED', count: tasks.filter(t => t.status === 'COMPLETED').length },
    { label: '失败', value: 'FAILED', count: tasks.filter(t => t.status === 'FAILED').length },
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

        <div className="flex gap-6 mb-4 text-sm border-b border-border pb-2 shrink-0">
          {tabs.map(tab => (
            <div 
              key={tab.value}
              onClick={() => setStatusFilter(tab.value)}
              className={cn(
                "cursor-pointer pb-2 -mb-[9px] transition-colors",
                statusFilter === tab.value 
                  ? "text-primary font-medium border-b-2 border-primary" 
                  : "text-muted-foreground hover:text-foreground"
              )}
            >
              {tab.label}{tab.count !== undefined ? ` (${tab.count})` : ''}
            </div>
          ))}
        </div>

        <div className="flex-1 overflow-y-auto custom-scrollbar">
          <table className="w-full text-sm text-left">
            <thead className="text-xs text-muted-foreground uppercase bg-muted/20 sticky top-0">
              <tr>
                <th className="px-4 py-3 rounded-l-lg font-medium">任务 ID</th>
                <th className="px-4 py-3 font-medium">任务名称</th>
                <th className="px-4 py-3 font-medium">状态</th>
                <th className="px-4 py-3 font-medium">相似度</th>
                <th className="px-4 py-3 font-medium">创建时间</th>
                <th className="px-4 py-3 rounded-r-lg font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {filteredTasks.length === 0 ? (
                <tr><td colSpan={6} className="text-center py-8 text-muted-foreground">暂无任务数据</td></tr>
              ) : filteredTasks.map((task, i) => (
                <tr key={i} className="border-b border-border/50 hover:bg-muted/20 transition-colors">
                  <td className="px-4 py-4 truncate max-w-[120px]" title={task.id}>{task.id.split('-')[0]}...</td>
                  <td className="px-4 py-4 font-medium text-foreground">{task.task_name}</td>
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
                  <td className="px-4 py-4 text-muted-foreground">{formatTime(task.created_at)}</td>
                  <td className="px-4 py-4">
                    <button 
                      onClick={() => navigate(`/tasks/${task.id}`)}
                      className={cn(
                        "flex items-center gap-1 cursor-pointer transition-colors font-medium",
                        task.status === 'COMPLETED' ? "text-primary hover:text-primary/80" : "text-muted-foreground cursor-not-allowed opacity-50"
                      )}
                      disabled={task.status !== 'COMPLETED'}
                    >
                      <Eye className="w-4 h-4" />
                      查看报告
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showCreation && <TaskCreation onClose={() => setShowCreation(false)} onTaskCreated={fetchTasks} />}
    </>
  );
}
