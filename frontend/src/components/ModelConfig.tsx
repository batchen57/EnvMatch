import React, { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Save, X, Cpu, Globe } from 'lucide-react';
import { cn } from '@/lib/utils';

export function ModelConfig() {
  const [models, setModels] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: '', identifier: '', provider: '', description: '', api_key: '', base_url: '', is_default: 'false' });

  const fetchModels = async () => {
    try {
      const res = await fetch("http://localhost:8000/models/");
      if (res.ok) {
        setModels(await res.json());
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchModels();
  }, []);

  const handleSave = async () => {
    try {
      if (editingId === 'new') {
        await fetch("http://localhost:8000/models/", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(editForm)
        });
      } else {
        await fetch(`http://localhost:8000/models/${editingId}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(editForm)
        });
      }
      setEditingId(null);
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确认删除该模型？')) return;
    try {
      await fetch(`http://localhost:8000/models/${id}`, { method: "DELETE" });
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-medium flex items-center gap-2"><Cpu className="w-5 h-5 text-primary" /> 模型管理</h3>
        <button 
          onClick={() => { setEditingId('new'); setEditForm({ name: '', identifier: '', provider: '', api_key: '', base_url: '', description: '', is_default: 'false' }); }}
          className="bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm flex items-center gap-2 hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          新增模型
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {models.map(m => (
          <div key={m.id} className={cn("border rounded-lg p-4 flex flex-col gap-3 relative", m.is_default === 'true' ? "bg-primary/5 border-primary/30" : "bg-muted/30 border-border")}>
            {m.is_default === 'true' && (
              <div className="absolute top-0 right-0 bg-primary text-primary-foreground text-[10px] px-2 py-0.5 rounded-bl-lg rounded-tr-lg">默认模型</div>
            )}
            
            {editingId === m.id ? (
              <div className="flex flex-col gap-3">
                <input value={editForm.name} onChange={e => setEditForm({...editForm, name: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="显示名称 (如: GPT-4o)" />
                <input value={editForm.identifier} onChange={e => setEditForm({...editForm, identifier: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="调用标识 (如: gpt-4o)" />
                <input value={editForm.provider} onChange={e => setEditForm({...editForm, provider: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="供应商 (如: OpenAI)" />
                <input value={editForm.api_key} onChange={e => setEditForm({...editForm, api_key: e.target.value})} type="password" className="bg-background border border-input rounded p-2 text-sm" placeholder="API Key (留空使用全局配置)" />
                <input value={editForm.base_url} onChange={e => setEditForm({...editForm, base_url: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="Base URL (可选)" />
                <textarea value={editForm.description} onChange={e => setEditForm({...editForm, description: e.target.value})} className="bg-background border border-input rounded p-2 text-sm h-16 resize-none custom-scrollbar" placeholder="模型描述..." />
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={editForm.is_default === 'true'} onChange={e => setEditForm({...editForm, is_default: e.target.checked ? 'true' : 'false'})} />
                  设为系统默认
                </label>
                <div className="flex justify-end gap-2 mt-2">
                  <button onClick={() => setEditingId(null)} className="text-muted-foreground hover:text-foreground p-1"><X className="w-4 h-4"/></button>
                  <button onClick={handleSave} className="text-primary hover:text-primary/80 p-1"><Save className="w-4 h-4"/></button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex justify-between items-start">
                  <div className="flex items-center gap-2">
                    <div className="w-8 h-8 rounded bg-background flex items-center justify-center border border-border shadow-sm">
                      <Cpu className="w-4 h-4 text-primary" />
                    </div>
                    <div>
                      <h4 className="font-medium text-foreground">{m.name}</h4>
                      <div className="text-xs text-muted-foreground flex items-center gap-1"><Globe className="w-3 h-3"/> {m.provider}</div>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button onClick={() => { setEditingId(m.id); setEditForm({name: m.name, identifier: m.identifier, provider: m.provider, api_key: m.api_key || '', base_url: m.base_url || '', description: m.description, is_default: m.is_default}); }} className="text-muted-foreground hover:text-foreground"><Edit2 className="w-4 h-4"/></button>
                    <button onClick={() => handleDelete(m.id)} className="text-muted-foreground hover:text-red-500"><Trash2 className="w-4 h-4"/></button>
                  </div>
                </div>
                <div className="bg-background/50 rounded p-2 text-xs font-mono text-muted-foreground break-all border border-border/50 flex flex-col gap-1">
                  <div>ID: {m.identifier}</div>
                  {m.base_url && <div>URL: {m.base_url}</div>}
                  <div>Key: {m.api_key ? '••••••••' + m.api_key.slice(-4) : '未配置'}</div>
                </div>
                <div className="text-sm text-muted-foreground line-clamp-2">{m.description}</div>
              </>
            )}
          </div>
        ))}

        {editingId === 'new' && (
          <div className="bg-muted/30 border border-primary/50 rounded-lg p-4 flex flex-col gap-3">
            <input value={editForm.name} onChange={e => setEditForm({...editForm, name: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="显示名称 (如: GPT-4o)" />
            <input value={editForm.identifier} onChange={e => setEditForm({...editForm, identifier: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="调用标识 (如: gpt-4o)" />
            <input value={editForm.provider} onChange={e => setEditForm({...editForm, provider: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="供应商 (如: OpenAI)" />
            <input value={editForm.api_key} onChange={e => setEditForm({...editForm, api_key: e.target.value})} type="password" className="bg-background border border-input rounded p-2 text-sm" placeholder="API Key (留空使用全局配置)" />
            <input value={editForm.base_url} onChange={e => setEditForm({...editForm, base_url: e.target.value})} className="bg-background border border-input rounded p-2 text-sm" placeholder="Base URL (可选)" />
            <textarea value={editForm.description} onChange={e => setEditForm({...editForm, description: e.target.value})} className="bg-background border border-input rounded p-2 text-sm h-16 resize-none custom-scrollbar" placeholder="模型描述..." />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={editForm.is_default === 'true'} onChange={e => setEditForm({...editForm, is_default: e.target.checked ? 'true' : 'false'})} />
              设为系统默认
            </label>
            <div className="flex justify-end gap-2 mt-2">
              <button onClick={() => setEditingId(null)} className="text-muted-foreground hover:text-foreground p-1"><X className="w-4 h-4"/></button>
              <button onClick={handleSave} className="text-primary hover:text-primary/80 p-1"><Save className="w-4 h-4"/></button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
