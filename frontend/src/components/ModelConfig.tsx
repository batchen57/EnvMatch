import { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, Save, X, Cpu, Globe, GripVertical, Info, Settings, FileText, Zap, Key } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  verticalListSortingStrategy,
  useSortable
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface SortableModelItemProps {
  m: any;
  setEditingId: (val: string | null) => void;
  handleDelete: (id: string) => void;
  className?: string;
}

function SortableModelItem({ m, setEditingId, handleDelete, className }: SortableModelItemProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: m.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 50 : 'auto',
    opacity: isDragging ? 0.5 : 1,
  };

  return (
    <div
      ref={setNodeRef}
      style={style}
      className={cn(
        "border rounded-lg pt-4 pr-2.5 pb-2.5 pl-2.5 flex flex-col gap-1.5 relative transition-colors bg-card hover:border-primary/50",
        m.is_default === 'true' ? "bg-primary/5 border-primary/30" : "border-border",
        isDragging && "shadow-xl border-primary",
        className
      )}
    >
      {m.is_default === 'true' && (
        <div className="absolute top-0 right-0 bg-primary text-primary-foreground text-[10px] px-2 py-0.5 rounded-bl-lg rounded-tr-lg">默认模型</div>
      )}
      <div className="flex justify-between items-start">
        <div className="flex items-center gap-2">
          <div className="w-7 h-7 rounded bg-primary/10 flex items-center justify-center border border-primary/20">
            <Cpu className="w-3.5 h-3.5 text-primary" />
          </div>
          <div className="min-w-0">
            <h4 className="font-semibold text-sm text-foreground truncate leading-tight">{m.name}</h4>
            <div className="text-xs text-muted-foreground flex items-center gap-1"><Globe className="w-3 h-3" /> {m.provider}</div>
          </div>
        </div>
        <div className="flex gap-1">
          <div {...attributes} {...listeners} className="p-1 text-muted-foreground hover:text-foreground cursor-grab active:cursor-grabbing rounded hover:bg-muted transition-colors"><GripVertical className="w-4 h-4" /></div>
          <button onClick={() => setEditingId(m.id)} className="text-muted-foreground hover:text-foreground p-1 rounded hover:bg-muted transition-colors"><Edit2 className="w-4 h-4" /></button>
          <button onClick={() => handleDelete(m.id)} className="text-muted-foreground hover:text-red-500 p-1 rounded hover:bg-muted transition-colors"><Trash2 className="w-4 h-4" /></button>
        </div>
      </div>
      <div className="bg-muted/40 rounded p-1.5 text-xs font-mono text-muted-foreground break-all border border-border/30 flex flex-col gap-0.5">
        <div className="truncate">ID: <span className="text-foreground/80">{m.identifier}</span></div>
        <div className="truncate">URL: <span className="text-foreground/80">{m.base_url || '—'}</span></div>
        <div className="truncate">Key: <span className="text-foreground/80">{m.api_key ? '••••••••' + m.api_key.slice(-4) : '未配置'}</span></div>
      </div>
      <div className="flex flex-wrap gap-1 mt-1">
        {(m.capabilities || []).map((cap: string) => (
          <span key={cap} className="text-[10px] px-1.5 py-0.5 rounded bg-muted border border-border text-muted-foreground flex items-center gap-1">
            {cap === 'text' && '文本'}
            {cap === 'image' && '图片'}
            {cap === 'video' && '视频'}
          </span>
        ))}
        {(!m.capabilities || m.capabilities.length === 0) && (
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-muted/50 border border-dashed border-border text-muted-foreground/50 italic">未标注形式</span>
        )}
      </div>
      <div className="text-sm text-muted-foreground leading-normal flex-1 line-clamp-2 mt-1">{m.description}</div>
    </div>
  );
}

export function ModelConfig() {
  const [models, setModels] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editForm, setEditForm] = useState({ 
    name: '', 
    identifier: '', 
    provider: '', 
    description: '', 
    api_key: '', 
    base_url: '', 
    is_default: 'false', 
    capabilities: [] as string[] 
  });

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 8 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates })
  );

  const fetchModels = async () => {
    try {
      const res = await fetch("http://localhost:8888/models/");
      if (res.ok) {
        const data = await res.json();
        setModels(data);
      }
    } catch (e) {
      console.error("Error fetching models:", e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchModels(); }, []);

  const presets = [
    { name: 'Gemini 2.0 Pro', provider: 'Google', identifier: 'gemini-2.0-pro-exp-02-05', base_url: '', description: 'Google 顶尖多模态模型，支持原生视频理解。', capabilities: ['text', 'image', 'video'] },
    { name: 'Qwen-VL-Max', provider: 'Alibaba', identifier: 'qwen-vl-max', base_url: 'https://dashscope.aliyuncs.com/compatible-mode/v1', description: '通义千问视觉大模型，视频理解能力强。', capabilities: ['text', 'image', 'video'] },
    { name: 'DeepSeek Chat', provider: 'DeepSeek', identifier: 'deepseek-chat', base_url: 'https://api.deepseek.com', description: '深度求索高性能模型，环境分析极具性价比。', capabilities: ['text'] },
    { name: 'GPT-4o', provider: 'OpenAI', identifier: 'gpt-4o', base_url: 'https://api.openai.com/v1', description: 'OpenAI 旗舰全能模型，推理能力卓越。', capabilities: ['text', 'image', 'video'] },
    { name: 'MiniMax-M2.7', provider: 'MiniMax', identifier: 'abab6.5s-chat', base_url: 'https://api.minimax.chat/v1', description: '国产大模型新锐，文本与视觉理解均衡。', capabilities: ['text', 'image'] },
  ];

  const handleApplyPreset = (presetName: string) => {
    const p = presets.find(x => x.name === presetName);
    if (p) {
      setEditForm({
        ...editForm,
        name: p.name,
        provider: p.provider,
        identifier: p.identifier,
        base_url: p.base_url,
        description: p.description,
        capabilities: p.capabilities || []
      });
    }
  };

  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;
    if (active.id !== over?.id) {
      const oldIndex = models.findIndex((i) => i.id === active.id);
      const newIndex = models.findIndex((i) => i.id === over?.id);
      const newItems = arrayMove(models, oldIndex, newIndex);
      setModels(newItems);
      
      // Update order in background
      for (let i = 0; i < newItems.length; i++) {
        fetch(`http://localhost:8888/models/${newItems[i].id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...newItems[i], sort_order: (i + 1) * 10 })
        });
      }
    }
  };

  const handleSave = async () => {
    try {
      const url = editingId === 'new' ? "http://localhost:8888/models/" : `http://localhost:8888/models/${editingId}`;
      const method = editingId === 'new' ? "POST" : "PUT";
      
      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ 
          ...editForm, 
          sort_order: editingId === 'new' ? models.length * 10 : 0 
        })
      });

      if (response.ok) {
        setEditingId(null);
        fetchModels();
      }
    } catch (e) {
      console.error("Error saving model:", e);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm('确认删除该模型？')) return;
    try {
      await fetch(`http://localhost:8888/models/${id}`, { method: "DELETE" });
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="bg-card rounded-xl p-6 border border-border h-full flex flex-col relative overflow-hidden">
      <div className="flex justify-between items-center mb-6">
        <h3 className="text-lg font-medium flex items-center gap-2"><Cpu className="w-5 h-5 text-primary" /> 模型管理</h3>
        <button
          onClick={() => { 
            setEditingId('new'); 
            setEditForm({ name: '', identifier: '', provider: '', api_key: '', base_url: '', description: '', is_default: 'false', capabilities: [] }); 
          }}
          className="bg-primary text-primary-foreground px-4 py-2 rounded-lg text-sm flex items-center gap-2 hover:bg-primary/90 transition-colors"
        >
          <Plus className="w-4 h-4" />
          新增模型
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 overflow-y-auto custom-scrollbar flex-1 pb-4 auto-rows-fr">
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={models.map(m => m.id)} strategy={verticalListSortingStrategy}>
            {models.map(m => (
              <SortableModelItem
                key={m.id} 
                m={m}
                setEditingId={(id) => {
                  if (id) {
                    const model = models.find(x => x.id === id);
                    if (model) {
                      setEditingId(id);
                      setEditForm({
                        name: model.name,
                        identifier: model.identifier,
                        provider: model.provider,
                        api_key: model.api_key || '',
                        base_url: model.base_url || '',
                        description: model.description || '',
                        is_default: model.is_default,
                        capabilities: model.capabilities || []
                      });
                    }
                  } else {
                    setEditingId(null);
                  }
                }}
                handleDelete={handleDelete}
              />
            ))}
          </SortableContext>
        </DndContext>
        {models.length === 0 && !loading && (
          <div className="col-span-full flex flex-col items-center justify-center py-12 text-muted-foreground border border-dashed border-border rounded-xl">
            <Cpu className="w-12 h-12 mb-4 opacity-20" />
            <p>暂无配置模型，请点击右上角新增</p>
          </div>
        )}
      </div>

      {editingId && (
        <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-[60] flex items-center justify-center p-4">
          <div className="bg-card border border-border rounded-2xl shadow-2xl w-full max-w-4xl flex flex-col overflow-hidden animate-in fade-in zoom-in duration-200">
            <div className="px-6 py-4 border-b border-border flex justify-between items-center bg-muted/30">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center border border-primary/20">
                  <Settings className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h4 className="font-bold text-foreground">{editingId === 'new' ? '新增 AI 模型' : '编辑模型配置'}</h4>
                  <p className="text-[10px] text-muted-foreground uppercase tracking-widest font-medium">Model Configuration System</p>
                </div>
              </div>
              <button onClick={() => setEditingId(null)} className="p-2 hover:bg-muted rounded-full transition-colors"><X className="w-5 h-5" /></button>
            </div>

            <div className="p-8 grid grid-cols-12 gap-8 overflow-y-auto max-h-[80vh] custom-scrollbar">
              {/* Left Column: Presets and Basic Info */}
              <div className="col-span-12 lg:col-span-5 space-y-6">
                <div className="space-y-3">
                  <label className="text-[10px] font-bold text-primary uppercase tracking-[0.2em] flex items-center gap-2 px-1">
                    <div className="w-1.5 h-1.5 rounded-full bg-primary" />
                    快速配置
                  </label>
                  <select
                    className="w-full bg-card border border-border rounded-xl px-4 py-3 text-sm outline-none focus:border-primary focus:ring-4 focus:ring-primary/10 text-foreground transition-all shadow-sm"
                    onChange={(e) => handleApplyPreset(e.target.value)}
                    defaultValue=""
                  >
                    <option value="" disabled>从官方预设模板快速加载...</option>
                    {presets.map(p => <option key={p.name} value={p.name}>{p.name}</option>)}
                  </select>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 px-1">
                    <Info className="w-4 h-4 text-primary" />
                    <label className="text-xs font-bold text-foreground">基本信息</label>
                  </div>
                  <div className="bg-muted/10 border border-border/50 rounded-2xl p-5 space-y-4">
                    <div className="space-y-1.5">
                      <label className="text-[10px] text-muted-foreground ml-1">模型显示名称</label>
                      <input 
                        value={editForm.name} 
                        onChange={e => setEditForm({ ...editForm, name: e.target.value })} 
                        className="w-full bg-background border border-border rounded-lg px-3 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/5 transition-all" 
                        placeholder="如: Gemini 2.0 Pro" 
                      />
                    </div>
                    <div className="space-y-1.5">
                      <label className="text-[10px] text-muted-foreground ml-1">技术供应商</label>
                      <input 
                        value={editForm.provider} 
                        onChange={e => setEditForm({ ...editForm, provider: e.target.value })} 
                        className="w-full bg-background border border-border rounded-lg px-3 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/5 transition-all" 
                        placeholder="如: Google" 
                      />
                    </div>
                    <div className="space-y-1.5">
                      <label className="text-[10px] text-muted-foreground ml-1">模型调用 ID</label>
                      <input 
                        value={editForm.identifier} 
                        onChange={e => setEditForm({ ...editForm, identifier: e.target.value })} 
                        className="w-full bg-background border border-border rounded-lg px-3 py-2.5 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/5 transition-all font-mono" 
                        placeholder="如: gemini-2.0-pro" 
                      />
                    </div>
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 px-1">
                    <FileText className="w-4 h-4 text-primary" />
                    <label className="text-xs font-bold text-foreground">功能描述</label>
                  </div>
                  <textarea 
                    value={editForm.description} 
                    onChange={e => setEditForm({ ...editForm, description: e.target.value })} 
                    className="w-full bg-muted/10 border border-border/50 rounded-2xl px-4 py-3 text-sm outline-none focus:border-primary focus:bg-background h-32 resize-none transition-all leading-relaxed custom-scrollbar" 
                    placeholder="描述该模型的分析优势、建议场景等..." 
                  />
                </div>
              </div>

              {/* Right Column: API and Capabilities */}
              <div className="col-span-12 lg:col-span-7 space-y-6">
                <div className="space-y-3">
                  <div className="flex items-center gap-2 px-1">
                    <Key className="w-4 h-4 text-primary" />
                    <label className="text-xs font-bold text-foreground">接口配置</label>
                  </div>
                  <div className="bg-muted/10 border border-border/50 rounded-2xl p-5 space-y-5">
                    <div className="space-y-1.5">
                      <label className="text-[10px] text-muted-foreground ml-1 flex items-center gap-1">API Key (密钥)</label>
                      <input 
                        value={editForm.api_key} 
                        onChange={e => setEditForm({ ...editForm, api_key: e.target.value })} 
                        type="password" 
                        className="w-full bg-background border border-border rounded-lg px-3 py-2.5 text-sm outline-none focus:border-primary transition-all" 
                        placeholder="在此输入您的密钥" 
                      />
                    </div>
                    <div className="space-y-1.5">
                      <label className="text-[10px] text-muted-foreground ml-1 flex items-center gap-1">Endpoint URL (接口地址)</label>
                      <input 
                        value={editForm.base_url} 
                        onChange={e => setEditForm({ ...editForm, base_url: e.target.value })} 
                        className="w-full bg-background border border-border rounded-lg px-3 py-2.5 text-sm outline-none focus:border-primary transition-all font-mono" 
                        placeholder="https://api.example.com/v1" 
                      />
                    </div>
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 px-1">
                    <Zap className="w-4 h-4 text-primary" />
                    <label className="text-xs font-bold text-foreground">支持能力与权重</label>
                  </div>
                  <div className="grid grid-cols-1 gap-3">
                    {[
                      { id: 'text', label: '文本识别', desc: 'Textual analysis' },
                      { id: 'image', label: '图片识别', desc: 'Visual understanding' },
                      { id: 'video', label: '视频识别', desc: 'Dynamic temporal reasoning' }
                    ].map(cap => (
                      <div 
                        key={cap.id}
                        onClick={() => {
                          const newCaps = editForm.capabilities.includes(cap.id)
                            ? editForm.capabilities.filter(x => x !== cap.id)
                            : [...editForm.capabilities, cap.id];
                          setEditForm({ ...editForm, capabilities: newCaps });
                        }}
                        className={cn(
                          "group flex items-center justify-between px-4 py-3 rounded-2xl border transition-all cursor-pointer select-none",
                          editForm.capabilities.includes(cap.id) 
                            ? "bg-primary/5 border-primary/40 shadow-sm" 
                            : "bg-muted/10 border-border/50 hover:border-primary/30"
                        )}
                      >
                        <div className="flex flex-col">
                          <span className={cn("text-sm font-semibold transition-colors", editForm.capabilities.includes(cap.id) ? "text-primary" : "text-foreground")}>{cap.label}</span>
                          <span className="text-[10px] text-muted-foreground uppercase tracking-wider">{cap.desc}</span>
                        </div>
                        <div className={cn(
                          "w-6 h-6 rounded-lg border flex items-center justify-center transition-all",
                          editForm.capabilities.includes(cap.id) 
                            ? "bg-primary border-primary shadow-lg shadow-primary/20" 
                            : "border-border bg-background group-hover:border-primary/30"
                        )}>
                          {editForm.capabilities.includes(cap.id) && <div className="w-2.5 h-2.5 rounded-full bg-white" />}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                <div 
                  onClick={() => setEditForm({ ...editForm, is_default: editForm.is_default === 'true' ? 'false' : 'true' })}
                  className={cn(
                    "flex items-center justify-between p-4 rounded-2xl border transition-all cursor-pointer select-none shadow-sm",
                    editForm.is_default === 'true' 
                      ? "bg-amber-500/10 border-amber-500/40" 
                      : "bg-muted/10 border-border/50 hover:border-primary/30"
                  )}
                >
                  <div className="flex items-center gap-4">
                    <div className={cn(
                      "w-10 h-10 rounded-xl flex items-center justify-center transition-all",
                      editForm.is_default === 'true' ? "bg-amber-500 text-white shadow-lg shadow-amber-500/20" : "bg-background text-muted-foreground border border-border"
                    )}>
                      <Zap className={cn("w-5 h-5", editForm.is_default === 'true' ? "fill-current" : "")} />
                    </div>
                    <div className="flex flex-col">
                      <span className={cn("text-sm font-bold", editForm.is_default === 'true' ? "text-amber-600" : "text-foreground")}>系统默认模型</span>
                      <span className="text-[10px] text-muted-foreground">新创建的任务将默认使用此模型进行分析</span>
                    </div>
                  </div>
                  <div className={cn(
                    "w-10 h-5 rounded-full relative transition-all duration-300",
                    editForm.is_default === 'true' ? "bg-amber-500" : "bg-muted"
                  )}>
                    <div className={cn(
                      "absolute top-1 w-3 h-3 rounded-full bg-white transition-all duration-300 shadow-sm",
                      editForm.is_default === 'true' ? "left-6" : "left-1"
                    )} />
                  </div>
                </div>
              </div>
            </div>

            <div className="px-8 py-5 bg-muted/30 border-t border-border flex justify-end items-center gap-4">
              <button 
                onClick={() => setEditingId(null)} 
                className="px-6 py-2.5 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
              >
                取消修改
              </button>
              <button 
                onClick={handleSave} 
                className="bg-primary text-primary-foreground px-8 py-2.5 rounded-xl text-sm font-bold hover:bg-primary/90 transition-all flex items-center gap-2 shadow-lg shadow-primary/20 active:scale-95"
              >
                <Save className="w-4 h-4" /> 保存配置信息
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
