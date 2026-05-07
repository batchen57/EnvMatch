import React, { useState, useRef, useEffect } from 'react';
import { Upload, Settings, Cpu, FileJson, X, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

export function TaskCreation({ onClose, onTaskCreated }: { onClose: () => void, onTaskCreated?: () => void }) {
  const [currentStep, setCurrentStep] = useState(1);
  const [taskName, setTaskName] = useState("");
  const [videoA, setVideoA] = useState<File | null>(null);
  const [videoB, setVideoB] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  
  const [prompts, setPrompts] = useState<any[]>([]);
  const [selectedPromptId, setSelectedPromptId] = useState<string>("");
  const [customPrompt, setCustomPrompt] = useState("");
  
  const [models, setModels] = useState<any[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<string>("");

  useEffect(() => {
    fetch("http://localhost:8000/prompt-templates/")
      .then(r => r.json())
      .then(data => setPrompts(data))
      .catch(e => console.error(e));
      
    fetch("http://localhost:8000/models/")
      .then(r => r.json())
      .then(data => {
        setModels(data);
        const defaultModel = data.find((m: any) => m.is_default === 'true');
        if (defaultModel) setSelectedModelId(defaultModel.identifier);
        else if (data.length > 0) setSelectedModelId(data[0].identifier);
      })
      .catch(e => console.error(e));
  }, []);

  const handlePromptSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value;
    setSelectedPromptId(id);
    if (id) {
      const p = prompts.find(x => x.id === id);
      if (p) setCustomPrompt(p.content);
    } else {
      setCustomPrompt("");
    }
  };
  
  const videoARef = useRef<HTMLInputElement>(null);
  const videoBRef = useRef<HTMLInputElement>(null);
  const steps = [
    { num: 1, title: '输入层' },
    { num: 2, title: '处理层' },
    { num: 3, title: '模型层' },
    { num: 4, title: '输出层' },
    { num: 5, title: '应用层' },
  ];

  const handleNext = async () => {
    if (currentStep === 1) {
      if (!taskName.trim()) {
        alert("请输入任务名称");
        return;
      }
      if (!videoA || !videoB) {
        alert("请上传视频素材 A 和 B");
        return;
      }
    }

    if (currentStep < 5) {
      setCurrentStep(currentStep + 1);
    } else {
      await handleSubmit();
    }
  };

  const handleSubmit = async () => {
    if (!videoA || !videoB || !taskName) {
      alert("请填写任务名称并上传视频 A 和 B");
      return;
    }
    
    setIsSubmitting(true);
    const formData = new FormData();
    formData.append("task_name", taskName);
    formData.append("video_a", videoA);
    formData.append("video_b", videoB);
    if (customPrompt) formData.append("prompt", customPrompt);
    if (selectedModelId) formData.append("model_id", selectedModelId);

    try {
      const res = await fetch("http://localhost:8000/tasks/", {
        method: "POST",
        body: formData,
      });
      if (res.ok) {
        if (onTaskCreated) onTaskCreated();
        onClose();
      } else {
        alert("创建任务失败");
      }
    } catch (err) {
      console.error(err);
      alert("网络错误");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handlePrev = () => {
    if (currentStep > 1) setCurrentStep(currentStep - 1);
  };

  return (
    <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-card w-full max-w-2xl rounded-xl border border-border overflow-hidden shadow-2xl flex flex-col">
        <div className="flex items-center justify-between p-4 border-b border-border">
          <h3 className="font-medium">新建任务分析流</h3>
          <button onClick={onClose} className="text-muted-foreground hover:text-foreground">
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="flex bg-muted/20 border-b border-border">
          {steps.map((s) => (
            <div key={s.num} className={cn(
              "flex-1 py-3 px-2 flex flex-col items-center gap-2 text-xs transition-colors",
              s.num === currentStep ? "text-primary border-b-2 border-primary" : 
              s.num < currentStep ? "text-emerald-500" : "text-muted-foreground"
            )}>
              <div className={cn(
                "w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold transition-colors",
                s.num === currentStep ? "bg-primary text-primary-foreground" : 
                s.num < currentStep ? "bg-emerald-500 text-white" : "border border-muted-foreground"
              )}>
                {s.num}
              </div>
              {s.title}
            </div>
          ))}
        </div>

        <div className="p-6 flex-1 min-h-[300px]">
          {currentStep === 1 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">视频输入</h4>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">视频 A</label>
                  <div 
                    className="border border-dashed border-border rounded-lg p-6 flex flex-col items-center justify-center text-center cursor-pointer hover:border-primary/50 transition-colors h-32"
                    onClick={() => videoARef.current?.click()}
                  >
                    <input type="file" className="hidden" ref={videoARef} onChange={(e) => setVideoA(e.target.files?.[0] || null)} accept="video/*,image/*" />
                    {videoA ? (
                      <span className="text-sm font-medium text-primary line-clamp-2">{videoA.name}</span>
                    ) : (
                      <>
                        <Upload className="w-6 h-6 text-muted-foreground mb-2" />
                        <div className="text-sm">点击上传测试素材 A</div>
                        <div className="text-[10px] text-muted-foreground mt-1">支持 mp4 / mov / jpg / png</div>
                      </>
                    )}
                  </div>
                </div>
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">视频 B</label>
                  <div 
                    className="border border-dashed border-border rounded-lg p-6 flex flex-col items-center justify-center text-center cursor-pointer hover:border-primary/50 transition-colors h-32"
                    onClick={() => videoBRef.current?.click()}
                  >
                    <input type="file" className="hidden" ref={videoBRef} onChange={(e) => setVideoB(e.target.files?.[0] || null)} accept="video/*,image/*" />
                    {videoB ? (
                      <span className="text-sm font-medium text-primary line-clamp-2">{videoB.name}</span>
                    ) : (
                      <>
                        <Upload className="w-6 h-6 text-muted-foreground mb-2" />
                        <div className="text-sm">点击上传测试素材 B</div>
                        <div className="text-[10px] text-muted-foreground mt-1">支持 mp4 / mov / jpg / png</div>
                      </>
                    )}
                  </div>
                </div>
              </div>
              <div className="mt-4 grid grid-cols-2 gap-4">
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">任务名称</label>
                  <input type="text" className="w-full bg-muted/50 border border-border rounded px-3 py-2 text-sm outline-none focus:border-primary" placeholder="请输入任务名称" value={taskName} onChange={e => setTaskName(e.target.value)} />
                </div>
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">提示词模版 (可选)</label>
                  <select 
                    value={selectedPromptId} 
                    onChange={handlePromptSelect}
                    className="w-full bg-muted/50 border border-border rounded px-3 py-2 text-sm outline-none focus:border-primary text-foreground"
                  >
                    <option value="">-- 使用系统默认提示词 --</option>
                    {prompts.map(p => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                </div>
              </div>
              {customPrompt && (
                <div className="mt-4">
                  <label className="text-xs text-muted-foreground mb-1 block">可微调提示词内容</label>
                  <textarea 
                    className="w-full bg-muted/50 border border-border rounded px-3 py-2 text-sm outline-none focus:border-primary h-24 resize-none custom-scrollbar"
                    value={customPrompt}
                    onChange={e => setCustomPrompt(e.target.value)}
                  />
                </div>
              )}
            </div>
          )}

          {currentStep === 2 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">预处理配置</h4>
              <div className="grid grid-cols-2 gap-3">
                {[
                  { icon: Settings, title: '抽帧采样', desc: '每秒 1-2 帧' },
                  { icon: Settings, title: '分辨率调整', desc: '长边 1280px' },
                  { icon: Settings, title: '格式转换', desc: '转为模型支持的格式' },
                  { icon: Settings, title: '去噪增强', desc: '提升画面质量' }
                ].map(item => (
                  <div key={item.title} className="flex items-center gap-3 p-3 bg-primary/10 border border-primary/50 rounded-lg cursor-pointer">
                    <item.icon className="w-4 h-4 text-primary" />
                    <div>
                      <div className="text-sm font-medium">{item.title}</div>
                      <div className="text-[10px] text-muted-foreground">{item.desc}</div>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {currentStep === 3 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">多模态模型选择</h4>
              <div className="space-y-3 max-h-64 overflow-y-auto custom-scrollbar pr-2">
                {models.map(m => (
                  <div 
                    key={m.id} 
                    onClick={() => setSelectedModelId(m.identifier)}
                    className={cn(
                      "p-4 rounded-lg cursor-pointer relative border transition-all",
                      selectedModelId === m.identifier ? "bg-primary/10 border-primary" : "bg-muted/20 border-border hover:border-primary/50"
                    )}
                  >
                    {m.is_default === 'true' && (
                      <div className="absolute top-0 right-0 bg-primary text-primary-foreground text-[10px] px-2 py-0.5 rounded-bl-lg rounded-tr-lg">默认模型</div>
                    )}
                    <div className="flex items-center gap-2 mb-1">
                      <Cpu className={cn("w-5 h-5", selectedModelId === m.identifier ? "text-primary" : "text-muted-foreground")} />
                      <span className={cn("text-base font-medium", selectedModelId === m.identifier ? "text-primary" : "")}>{m.name}</span>
                      <span className="text-[10px] bg-background border border-border px-1.5 py-0.5 rounded text-muted-foreground ml-auto">{m.provider}</span>
                    </div>
                    <div className="text-xs text-muted-foreground line-clamp-2">{m.description}</div>
                  </div>
                ))}
                {models.length === 0 && (
                  <div className="text-sm text-muted-foreground text-center py-8 border border-dashed border-border rounded-lg">
                    暂未配置任何模型，请前往“模型管理”添加。
                  </div>
                )}
              </div>
            </div>
          )}

          {currentStep === 4 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">输出报告配置</h4>
              <div className="grid grid-cols-2 gap-3">
                {['相似度评分', '维度评分明细', '对比分析报告', 'JSON 数据输出'].map(title => (
                  <div key={title} className="flex items-center gap-3 p-3 bg-muted/20 border border-border rounded-lg">
                    <FileJson className="w-4 h-4 text-primary" />
                    <span className="text-sm font-medium">{title}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {currentStep === 5 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">应用与部署</h4>
              <div className="space-y-4">
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">生成 API Key 用于三方调用</label>
                  <div className="flex items-center bg-muted/50 border border-border rounded p-2 text-sm">
                    <span className="flex-1 text-muted-foreground truncate">点击下方完成按钮后自动生成</span>
                  </div>
                </div>
                <div>
                  <label className="text-xs text-muted-foreground mb-1 block">配置 Webhook 回调地址 (可选)</label>
                  <input type="text" className="w-full bg-muted/50 border border-border rounded px-3 py-2 text-sm outline-none" placeholder="https://your-domain.com/webhook" />
                </div>
              </div>
            </div>
          )}
        </div>

        <div className="p-4 border-t border-border flex justify-between bg-muted/10">
          <button 
            onClick={handlePrev}
            disabled={currentStep === 1 || isSubmitting}
            className="px-4 py-2 rounded-lg text-sm bg-muted text-foreground disabled:opacity-50 cursor-pointer"
          >
            上一步
          </button>
          <button 
            onClick={handleNext}
            disabled={isSubmitting}
            className="px-4 py-2 rounded-lg text-sm bg-primary text-primary-foreground hover:bg-primary/90 cursor-pointer flex items-center gap-2 disabled:opacity-50"
          >
            {isSubmitting && <Loader2 className="w-4 h-4 animate-spin" />}
            {currentStep === 5 ? '完成创建' : '下一步'}
          </button>
        </div>
      </div>
    </div>
  );
}
