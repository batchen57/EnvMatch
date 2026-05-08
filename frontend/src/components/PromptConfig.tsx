import React, { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Save, X, FileText, Clock, Search } from 'lucide-react';
import { cn } from '@/lib/utils';

export function PromptConfig() {
  const [prompts, setPrompts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: '', content: '' });
  const [searchQuery, setSearchQuery] = useState("");

  const fetchPrompts = async () => {
    try {
      const res = await fetch("http://localhost:8000/prompt-templates/");
      if (res.ok) {
        setPrompts(await res.json());
      }
    } catch (e) {
      console.error("Error fetching prompts:", e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPrompts();
  }, []);

  const handleSave = async () => {
    if (!editForm.name.trim() || !editForm.content.trim()) return;

    try {
      const url = editingId === 'new' 
        ? "http://localhost:8000/prompt-templates/" 
        : `http://localhost:8000/prompt-templates/${editingId}`;
      const method = editingId === 'new' ? "POST" : "PUT";

      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(editForm)
      });

      if (response.ok) {
        setEditingId(null);
        fetchPrompts();
      }
    } catch (e) {
      console.error("Error saving prompt:", e);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确认删除该提示词模版？删除后不可恢复。')) return;
    try {
      await fetch(`http://localhost:8000/prompt-templates/${id}`, { method: "DELETE" });
      fetchPrompts();
    } catch (e) {
      console.error(e);
    }
  };

  const filteredPrompts = prompts.filter(p =>
    p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    p.content.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col relative overflow-hidden">
      <div className="flex justify-between items-center mb-8">
        <div className="flex flex-col gap-1">
          <h3 className="text-xl font-bold flex items-center gap-2">
            <FileText className="w-6 h-6 text-primary" />
            提示词模版库
          </h3>
          <p className="text-xs text-muted-foreground">管理多模态模型分析所需的各种环境对比提示词</p>
        </div>

        <div className="flex items-center gap-4">
          <div className="relative group">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground group-focus-within:text-primary transition-colors" />
            <input
              type="text"
              placeholder="搜索模版..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="bg-muted/50 border border-border rounded-lg pl-9 pr-4 py-2 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 transition-all w-64"
            />
          </div>
          <button
            onClick={() => { setEditingId('new'); setEditForm({ name: '', content: '' }); }}
            className="bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm font-medium flex items-center gap-2 hover:bg-primary/90 transition-all shadow-lg shadow-primary/20"
          >
            <Plus className="w-4 h-4" />
            新建模版
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-6 overflow-y-auto custom-scrollbar flex-1 pb-6 pr-2">
        {filteredPrompts.map(p => (
          <div
            key={p.id}
            className="group bg-muted/20 border border-border rounded-xl p-5 flex flex-col gap-4 hover:border-primary/50 hover:bg-muted/30 transition-all duration-300 relative overflow-hidden shadow-sm"
          >
            <div className="absolute top-0 right-0 w-24 h-24 bg-primary/5 rounded-full -mr-12 -mt-12 transition-all group-hover:bg-primary/10" />

            <div className="flex justify-between items-start relative z-10">
              <h4 className="font-bold text-foreground text-base group-hover:text-primary transition-colors truncate pr-12">{p.name}</h4>
              <div className="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity absolute right-0">
                <button
                  onClick={() => { setEditingId(p.id); setEditForm({ name: p.name, content: p.content }); }}
                  className="p-1.5 bg-background border border-border rounded-lg text-muted-foreground hover:text-primary hover:border-primary/50 transition-all"
                  title="编辑"
                >
                  <Edit2 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => handleDelete(p.id)}
                  className="p-1.5 bg-background border border-border rounded-lg text-muted-foreground hover:text-red-500 hover:border-red-500/50 transition-all"
                  title="删除"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            <div className="text-[13px] text-muted-foreground line-clamp-6 leading-relaxed flex-1 whitespace-pre-wrap italic">
              "{p.content}"
            </div>

            <div className="flex items-center gap-2 text-[10px] text-muted-foreground/60 border-t border-border/50 pt-3">
              <Clock className="w-3 h-3" />
              <span>更新于 {new Date(p.updated_at).toLocaleString()}</span>
            </div>
          </div>
        ))}

        {filteredPrompts.length === 0 && !loading && (
          <div className="col-span-full py-20 flex flex-col items-center justify-center text-muted-foreground border-2 border-dashed border-border rounded-2xl bg-muted/10">
            <FileText className="w-12 h-12 mb-4 opacity-20" />
            <p className="text-sm font-medium">没有找到相关的提示词模版</p>
            <button
              onClick={() => setSearchQuery("")}
              className="mt-2 text-primary text-xs hover:underline"
            >
              清空搜索条件
            </button>
          </div>
        )}
      </div>

      <div className="mt-4 p-4 bg-muted/20 rounded-xl border border-border/50">
        <p className="text-[10px] text-muted-foreground px-1 italic">
          提示：提示词质量直接影响模型对比结果的准确度。建议明确区分室内/室外、地貌、固定陈设等维度。
        </p>
      </div>

      {/* Modal Dialog */}
      {editingId && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-[100] flex items-center justify-center p-4">
          <div className="bg-card border border-border rounded-2xl shadow-2xl w-full max-w-xl flex flex-col overflow-hidden animate-in fade-in zoom-in duration-300">
            <div className="px-6 py-5 border-b border-border flex justify-between items-center bg-muted/30">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center border border-primary/20">
                  <FileText className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h4 className="font-bold text-lg">{editingId === 'new' ? '新建提示词模版' : '编辑模版内容'}</h4>
                  <p className="text-[10px] text-muted-foreground uppercase tracking-wider">Analysis Prompt Engineering</p>
                </div>
              </div>
              <button
                onClick={() => setEditingId(null)}
                className="p-2 hover:bg-red-500/10 hover:text-red-500 rounded-full transition-all"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="p-6 space-y-6 bg-muted/5">
              <div className="space-y-2">
                <label className="text-xs font-bold text-foreground flex items-center gap-2">
                  模版名称
                  <span className="text-red-500">*</span>
                </label>
                <input
                  value={editForm.name}
                  onChange={e => setEditForm({ ...editForm, name: e.target.value })}
                  className="w-full bg-card border border-border rounded-xl px-4 py-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 transition-all shadow-sm"
                  placeholder="例如: 室内信贷场景对比、外卖骑手背景分析..."
                />
              </div>

              <div className="space-y-2">
                <label className="text-xs font-bold text-foreground flex items-center gap-2">
                  提示词内容
                  <span className="text-red-500">*</span>
                </label>
                <div className="relative group">
                  <textarea
                    value={editForm.content}
                    onChange={e => setEditForm({ ...editForm, content: e.target.value })}
                    className="w-full bg-card border border-border rounded-xl px-4 py-4 text-[13px] outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 h-64 resize-none custom-scrollbar transition-all leading-relaxed shadow-sm"
                    placeholder="请输入详细的 AI 分析指令，建议包含相似度打分维度、JSON 输出格式要求等..."
                  />
                  <div className="absolute bottom-3 right-3 text-[10px] text-muted-foreground pointer-events-none bg-muted/50 px-2 py-1 rounded-md border border-border">
                    Markdown Supported
                  </div>
                </div>
              </div>
            </div>

            <div className="p-4 bg-muted/30 border-t border-border flex justify-end gap-3 px-6 py-4">
              <button
                onClick={() => setEditingId(null)}
                className="px-6 py-2.5 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
              >
                放弃修改
              </button>
              <button
                onClick={handleSave}
                className="bg-primary text-primary-foreground px-8 py-2.5 rounded-xl text-sm font-bold hover:bg-primary/90 transition-all flex items-center gap-2 shadow-lg shadow-primary/20 active:scale-95"
              >
                <Save className="w-4 h-4" />
                确认保存
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}