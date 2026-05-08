import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { Sidebar } from './components/Sidebar';
import { Pipeline } from './components/Pipeline';
import { TaskList } from './components/TaskList';
import { ResultDetails } from './components/ResultDetails';
import { Overview } from './components/Overview';
import { ResultReport } from './components/ResultReport';
import { PromptConfig } from './components/PromptConfig';
import { ModelConfig } from './components/ModelConfig';
import { Bell, HelpCircle } from 'lucide-react';
function Dashboard() {
  return (
    <div className="flex flex-col gap-6">
      <Pipeline />
      <Overview />
    </div>
  );
}
function App() {
  return (
    <div className="flex h-screen bg-background text-foreground font-sans overflow-hidden">
      <Sidebar />

      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <header className="h-16 shrink-0 flex items-center justify-between px-8 border-b border-border bg-card/50 backdrop-blur">
          <div className="flex flex-col">
            <h1 className="text-lg font-bold tracking-wide">视频环境相似度对比平台</h1>
            <span className="text-xs text-muted-foreground">基于多模态大模型的环境相似度分析系统</span>
          </div>
          <div className="flex items-center gap-4">
            <button className="text-muted-foreground hover:text-foreground transition-colors cursor-pointer">
              <Bell className="w-5 h-5" />
            </button>
            <button className="text-muted-foreground hover:text-foreground transition-colors cursor-pointer">
              <HelpCircle className="w-5 h-5" />
            </button>
            <div className="flex items-center gap-2 border-l border-border pl-4 cursor-pointer hover:opacity-80 transition-opacity">
              <div className="w-8 h-8 rounded-full bg-blue-500 overflow-hidden">
                <img src="https://api.dicebear.com/7.x/avataaars/svg?seed=Felix" alt="avatar" />
              </div>
              <span className="text-sm font-medium">张三</span>
            </div>
          </div>
        </header>
        <main className="flex-1 p-6 overflow-y-auto custom-scrollbar">
          <Routes>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<Dashboard />} />
            <Route path="/tasks" element={<TaskList />} />
            <Route path="/results" element={<ResultReport />} />
            <Route path="/tasks/:id" element={<ResultDetails />} />
            <Route path="/prompts" element={<PromptConfig />} />
            <Route path="/models" element={<ModelConfig />} />
            <Route path="*" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}
export default App;
