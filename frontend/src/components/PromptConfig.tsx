import React, { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Save, X } from 'lucide-react';

export function PromptConfig() {
  const [prompts, setPrompts] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ name: '', content: '' });

  const fetchPrompts = async () => {
    try {
      const res = await fetch("http://localhost:8000/prompt-templates/");
      if (res.ok) {
        setPrompts(await res.json());
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchPrompts();
  }, []);

  const handleSave = async () => {
    try {
      if (editingId === 'new') {
        await fetch("http://localhost:8000/prompt-templates/", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(editForm)
        });
      } else {
        await fetch(`http://localhost:8000/prompt-templates/${editingId}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(editForm)
        });
      }
      setEditingId(null);
      fetchPrompts();
    } catch (e) {
      console.error(e);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确认删除？')) return;
    try {
      await fetch(`http://localhost:8000/prompt-templates/${id}`, { method: "DELETE" });
      fetchPrompts();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-medium">相似背景提示词模版</h3>
        <button 
          onClick={() => { setEditingId('new'); setEditForm({ name: '', content: '' }); }}
          className="bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm flex items-center gap-2 hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          新增模版
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {prompts.map(p => (
          <div key={p.id} className="bg-muted/30 border border-border rounded-lg p-4 flex flex-col gap-3">
            {editingId === p.id ? (
              <div className="flex flex-col gap-3">
                <input 
                  value={editForm.name} 
                  onChange={e => setEditForm({...editForm, name: e.target.value})} 
                  className="bg-background border border-input rounded p-2 text-sm"
                  placeholder="模版名称"
                />
                <textarea 
                  value={editForm.content} 
                  onChange={e => setEditForm({...editForm, content: e.target.value})} 
                  className="bg-background border border-input rounded p-2 text-sm h-32 resize-none custom-scrollbar"
                  placeholder="提示词内容..."
                />
                <div className="flex justify-end gap-2 mt-2">
                  <button onClick={() => setEditingId(null)} className="text-muted-foreground hover:text-foreground p-1"><X className="w-4 h-4"/></button>
                  <button onClick={handleSave} className="text-primary hover:text-primary/80 p-1"><Save className="w-4 h-4"/></button>
                </div>
              </div>
            ) : (
              <>
                <div className="flex justify-between items-start">
                  <h4 className="font-medium text-primary">{p.name}</h4>
                  <div className="flex gap-2">
                    <button onClick={() => { setEditingId(p.id); setEditForm({name: p.name, content: p.content}); }} className="text-muted-foreground hover:text-foreground"><Edit2 className="w-4 h-4"/></button>
                    <button onClick={() => handleDelete(p.id)} className="text-muted-foreground hover:text-red-500"><Trash2 className="w-4 h-4"/></button>
                  </div>
                </div>
                <div className="text-sm text-muted-foreground line-clamp-4 whitespace-pre-wrap">{p.content}</div>
                <div className="text-xs text-muted-foreground/50 mt-auto pt-2">更新于: {new Date(p.updated_at).toLocaleString()}</div>
              </>
            )}
          </div>
        ))}

        {editingId === 'new' && (
          <div className="bg-muted/30 border border-primary/50 rounded-lg p-4 flex flex-col gap-3">
            <input 
              value={editForm.name} 
              onChange={e => setEditForm({...editForm, name: e.target.value})} 
              className="bg-background border border-input rounded p-2 text-sm"
              placeholder="模版名称 (如: 借贷场景专属)"
            />
            <textarea 
              value={editForm.content} 
              onChange={e => setEditForm({...editForm, content: e.target.value})} 
              className="bg-background border border-input rounded p-2 text-sm h-32 resize-none custom-scrollbar"
              placeholder="请描述对比的维度要求..."
            />
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
