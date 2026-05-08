import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Home, List, BarChart2, FileText, Box } from 'lucide-react';
import { cn } from '@/lib/utils';
export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();

  const navItems = [
    { icon: Home, label: '工作台', path: '/dashboard' },
    { icon: List, label: '任务管理', path: '/tasks' },
    { icon: BarChart2, label: '结果报表', path: '/results' },
    { icon: FileText, label: '提示词配置', path: '/prompts' },
    { icon: Box, label: '模型管理', path: '/models' },
  ];
  return (
    <div className="w-64 bg-card border-r border-border h-screen flex flex-col justify-between shrink-0 sticky top-0 overflow-y-auto">
      <div>
        <div className="h-16 flex items-center px-6 border-b border-border cursor-pointer" onClick={() => navigate('/dashboard')}>
          <div className="flex items-center gap-2 text-primary font-bold text-xl">
            <Box className="h-6 w-6" />
            EnvMatch AI
          </div>
        </div>
        <div className="p-4 flex flex-col gap-2">
          {navItems.map((item) => {
            const active = location.pathname.startsWith(item.path);
            return (
              <div
                key={item.label}
                onClick={() => navigate(item.path)}
                className={cn(
                  "flex items-center gap-3 px-4 py-3 rounded-lg cursor-pointer transition-colors text-sm",
                  active ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted hover:text-foreground"
                )}
              >
                <item.icon className="h-5 w-5" />
                {item.label}
              </div>
            );
          })}
        </div>
      </div>

      <div className="p-4 border-t border-border flex items-center gap-3 cursor-pointer hover:bg-muted transition-colors m-4 rounded-lg">
        <div className="h-10 w-10 rounded-full bg-blue-500 overflow-hidden">
          <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix" alt="avatar" />
        </div>
        <div className="flex-1">
          <div className="text-sm font-medium">张三</div>
          <div className="text-xs text-muted-foreground">系统管理员</div>
        </div>
      </div>
    </div>
  );
}