import React, { useState, useRef, useEffect } from 'react';
import { Upload, Settings, Cpu, FileJson, X, Loader2, Scan, Video, Zap } from 'lucide-react';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

export function TaskCreation({ onClose, onTaskCreated }: { onClose: () => void, onTaskCreated?: () => void }) {
  const [currentStep, setCurrentStep] = useState(1);
  const [taskName, setTaskName] = useState("");
  const [videoA, setVideoA] = useState<File | null>(null);
  const [videoB, setVideoB] = useState<File | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [preprocessOptions, setPreprocessOptions] = useState({
    recognition_mode: 'image', // 'image' | 'video'
    sampling_type: 'fixed',    // 'fixed' | 'perceptual'
    sampling_fps: 1,
    resolution: false,
    resolution_val: 720,
    format_convert: false,
    denoise: false
  });

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
        setError("请输入任务名称");
        return;
      }
      if (!videoA || !videoB) {
        setError("请上传视频素材 A 和 B");
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
      setError("请填写任务名称并上传视频 A 和 B");
      return;
    }

    setIsSubmitting(true);
    const formData = new FormData();
    formData.append("task_name", taskName);
    formData.append("video_a", videoA);
    formData.append("video_b", videoB);
    if (customPrompt) formData.append("prompt", customPrompt);
    if (selectedModelId) formData.append("model_id", selectedModelId);
    formData.append("preprocess_options", JSON.stringify(preprocessOptions));

    try {
      const res = await fetch("http://localhost:8000/tasks/", {
        method: "POST",
        body: formData,
      });
      if (res.ok) {
        if (onTaskCreated) onTaskCreated();
        onClose();
      } else {
        setError("创建任务失败，请检查模型配置及网络状态");
      }
    } catch (err) {
      console.error(err);
      setError("网络连接错误，请稍后重试");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handlePrev = () => {
    if (currentStep > 1) setCurrentStep(currentStep - 1);
  };

  const handleRecognitionModeChange = (mode: 'image' | 'video') => {
    setPreprocessOptions(prev => ({ ...prev, recognition_mode: mode }));

    // Auto-select first valid model if current one becomes invalid
    const validModels = models.filter(m => (m.capabilities || []).includes(mode));
    if (validModels.length > 0 && !validModels.some(m => m.identifier === selectedModelId)) {
      // Find default model among valid ones
      const defaultModel = validModels.find(m => m.is_default === 'true');
      setSelectedModelId(defaultModel ? defaultModel.identifier : validModels[0].identifier);
    }
  };

  return (
    <div className="fixed inset-0 bg-background/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <motion.div 
        initial={{ opacity: 0, scale: 0.95, y: 20 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        drag
        dragMomentum={false}
        dragControls={undefined}
        className="bg-card border border-border rounded-xl shadow-2xl flex flex-col relative overflow-hidden"
        style={{ 
          width: '800px', 
          height: 'min(90vh, 600px)',
          resize: 'both',
          minWidth: '600px',
          minHeight: '450px'
        }}
      >
        {/* Resize Handle Icon (Visual hint) */}
        <div className="absolute bottom-1 right-1 w-4 h-4 cursor-nwse-resize opacity-20 hover:opacity-100 transition-opacity">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" className="w-full h-full"><path d="M21 15L15 21M21 9L9 21" /></svg>
        </div>

        {/* Modal Header - Draggable Area */}
        <div className="px-6 py-4 border-b border-border flex justify-between items-center bg-muted/30 cursor-move active:cursor-grabbing select-none">
          <div>
            <h2 className="text-xl font-semibold tracking-tight">新建任务分析流</h2>
            <p className="text-xs text-muted-foreground mt-0.5">配置您的多模态视频分析引擎</p>
          </div>
          <button 
            onClick={onClose}
            className="p-2 hover:bg-red-500/10 hover:text-red-500 rounded-full transition-all cursor-pointer"
          >
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

        {/* Custom Error Notification */}
        {error && (
          <div className="bg-red-500/10 border-b border-red-500/20 px-4 py-2 flex items-center justify-between animate-in slide-in-from-top duration-300">
            <span className="text-xs text-red-500 font-medium flex items-center gap-2">
              <span className="w-1.5 h-1.5 rounded-full bg-red-500 animate-pulse" />
              {error}
            </span>
            <button onClick={() => setError(null)} className="text-red-500 hover:text-red-600">
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        )}

        <div className="p-6 flex-1 min-h-[300px] overflow-y-auto custom-scrollbar">
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
              {selectedPromptId && customPrompt !== undefined && (
                <div className="mt-6 p-4 bg-muted/30 rounded-lg border border-border/50">
                  <label className="block text-sm font-medium mb-2 text-primary flex items-center gap-2">
                    <div className="w-1.5 h-1.5 rounded-full bg-primary animate-pulse" />
                    可微调提示词内容
                  </label>
                  <textarea 
                    className="w-full h-48 bg-background border border-border rounded-md p-3 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 transition-all resize-none custom-scrollbar leading-relaxed"
                    placeholder="在此输入或调整 AI 分析指令..."
                    value={customPrompt}
                    onChange={e => setCustomPrompt(e.target.value)}
                  />
                  <p className="mt-2 text-[10px] text-muted-foreground">提示：建议基于模版进行微调，以包含特定的环境比对细节。</p>
                </div>
              )}
            </div>
          )}

          {currentStep === 2 && (
            <div className="space-y-6 animate-in fade-in duration-300">
              {/* 1. 模型识别模式 */}
              <div>
                <h4 className="font-medium text-sm mb-3 flex items-center gap-2">
                  <Scan className="w-4 h-4 text-primary" />
                  模型识别模式
                </h4>
                <div className="grid grid-cols-2 gap-3">
                  <div 
                    onClick={() => handleRecognitionModeChange('image')}
                    className={cn(
                      "p-3 border rounded-lg cursor-pointer transition-all",
                      preprocessOptions.recognition_mode === 'image' 
                        ? "bg-primary/10 border-primary shadow-sm" 
                        : "bg-muted/10 border-border hover:border-primary/30"
                    )}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="text-sm font-medium">抽帧图片LLM识别</div>
                      <div className={cn(
                        "w-4 h-4 rounded-full border flex items-center justify-center",
                        preprocessOptions.recognition_mode === 'image' ? "border-primary bg-primary" : "border-muted-foreground"
                      )}>
                        {preprocessOptions.recognition_mode === 'image' && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                      </div>
                    </div>
                    <div className="text-[10px] text-muted-foreground">将视频抽离为关键帧，通过视觉语言模型进行静态分析。</div>
                  </div>

                  <div 
                    onClick={() => handleRecognitionModeChange('video')}
                    className={cn(
                      "p-3 border rounded-lg cursor-pointer transition-all",
                      preprocessOptions.recognition_mode === 'video' 
                        ? "bg-primary/10 border-primary shadow-sm" 
                        : "bg-muted/10 border-border hover:border-primary/30"
                    )}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="text-sm font-medium">视频LLM识别</div>
                      <div className={cn(
                        "w-4 h-4 rounded-full border flex items-center justify-center",
                        preprocessOptions.recognition_mode === 'video' ? "border-primary bg-primary" : "border-muted-foreground"
                      )}>
                        {preprocessOptions.recognition_mode === 'video' && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                      </div>
                    </div>
                    <div className="text-[10px] text-muted-foreground">直接上传视频流，利用支持长视频上下文的模型进行动态分析。</div>
                  </div>
                </div>

                {/* 模式特定配置 */}
                <div className="mt-3 p-4 bg-muted/10 border border-border rounded-lg space-y-4">
                  <div>
                    <label className="text-xs font-medium text-primary flex items-center gap-2 mb-2">
                      <Zap className="w-3 h-3" />
                      采样配置
                    </label>
                    <div className="flex flex-wrap gap-4">
                      <div 
                        className="flex items-center gap-2 cursor-pointer"
                        onClick={() => setPreprocessOptions({...preprocessOptions, sampling_type: 'fixed'})}
                      >
                        <div className={cn(
                          "w-3 h-3 rounded-full border flex items-center justify-center",
                          preprocessOptions.sampling_type === 'fixed' ? "border-primary bg-primary" : "border-muted-foreground"
                        )}>
                          {preprocessOptions.sampling_type === 'fixed' && <div className="w-1 h-1 rounded-full bg-white" />}
                        </div>
                        <span className="text-xs">按秒抽帧</span>
                      </div>
                      
                      {preprocessOptions.recognition_mode === 'image' && (
                        <div 
                          className="flex items-center gap-2 cursor-pointer"
                          onClick={() => setPreprocessOptions({...preprocessOptions, sampling_type: 'perceptual'})}
                        >
                          <div className={cn(
                            "w-3 h-3 rounded-full border flex items-center justify-center",
                            preprocessOptions.sampling_type === 'perceptual' ? "border-primary bg-primary" : "border-muted-foreground"
                          )}>
                            {preprocessOptions.sampling_type === 'perceptual' && <div className="w-1 h-1 rounded-full bg-white" />}
                          </div>
                          <span className="text-xs">感知抽帧</span>
                        </div>
                      )}
                    </div>
                  </div>

                  {preprocessOptions.sampling_type === 'fixed' && (
                    <div className="flex items-center gap-3 pt-1 border-t border-border/50">
                      <span className="text-[10px] text-muted-foreground">采样频率:</span>
                      <div className="flex gap-1.5">
                        {[1, 2, 5].map(v => (
                          <button 
                            key={v} 
                            onClick={() => setPreprocessOptions({...preprocessOptions, sampling_fps: v})}
                            className={cn(
                              "text-[10px] px-2 py-0.5 rounded border transition-colors", 
                              preprocessOptions.sampling_fps === v ? "bg-primary text-white border-primary" : "border-border hover:border-primary/50"
                            )}
                          >
                            {v}fps
                          </button>
                        ))}
                      </div>
                    </div>
                  )}

                  {preprocessOptions.sampling_type === 'perceptual' && preprocessOptions.recognition_mode === 'image' && (
                    <div className="pt-2 border-t border-border/50 space-y-1.5">
                      <p className="text-[10px] text-emerald-500 font-medium flex items-center gap-1">
                        <Zap className="w-3 h-3" />
                        感知模式已激活：PySceneDetect + Optical Flow
                      </p>
                      <p className="text-[10px] text-muted-foreground leading-relaxed">
                        基于 ContentDetector 锁定像素剧变瞬间（如车辆经过、室内外切换），结合光流法追踪摄像机位移。当位移累计超过 30% 画面宽度时强制补帧，确保极慢速扫摄不失真。
                      </p>
                    </div>
                  )}
                </div>
              </div>

              {/* 2. 视频预处理配置 */}
              <div>
                <h4 className="font-medium text-sm mb-3 flex items-center gap-2">
                  <Settings className="w-4 h-4 text-primary" />
                  视频预处理配置
                </h4>
                <div className="grid grid-cols-3 gap-3">
                  {[
                    { key: 'resolution', title: '分辨率调整', desc: '目标高度', sub: `${preprocessOptions.resolution_val}p` },
                    { key: 'format_convert', title: '格式转换', desc: '转为 MP4', sub: '兼容性' },
                    { key: 'denoise', title: '去噪增强', desc: '画面优化', sub: '画质提升' }
                  ].map(item => (
                    <div 
                      key={item.key} 
                      onClick={() => setPreprocessOptions({...preprocessOptions, [item.key]: !preprocessOptions[item.key as keyof typeof preprocessOptions]})}
                      className={cn(
                        "flex flex-col gap-2 p-3 border rounded-lg cursor-pointer transition-all",
                        preprocessOptions[item.key as keyof typeof preprocessOptions] 
                          ? "bg-primary/10 border-primary shadow-sm" 
                          : "bg-muted/10 border-border hover:border-primary/30"
                      )}
                    >
                      <div className="flex items-center justify-between">
                        <div className="text-xs font-medium">{item.title}</div>
                        <div className={cn(
                          "w-3 h-3 rounded-full border flex items-center justify-center",
                          preprocessOptions[item.key as keyof typeof preprocessOptions] ? "border-primary bg-primary" : "border-muted-foreground"
                        )}>
                          {preprocessOptions[item.key as keyof typeof preprocessOptions] && <div className="w-1 h-1 rounded-full bg-white" />}
                        </div>
                      </div>
                      
                      <div className="flex items-center justify-between mt-0.5">
                        <div className="text-[9px] text-muted-foreground">{item.desc}</div>
                        <div className="text-[9px] font-mono text-primary bg-primary/5 px-1 rounded">{item.sub}</div>
                      </div>

                      {preprocessOptions[item.key as keyof typeof preprocessOptions] && item.key === 'resolution' && (
                        <div className="mt-2 flex flex-wrap gap-1" onClick={e => e.stopPropagation()}>
                          {[360, 480, 720, 1080].map(v => (
                            <button 
                              key={v} 
                              onClick={() => setPreprocessOptions({...preprocessOptions, resolution_val: v})}
                              className={cn("text-[8px] px-1.5 py-0.5 rounded border transition-colors", preprocessOptions.resolution_val === v ? "bg-primary text-white border-primary" : "border-border hover:border-primary/50")}
                            >
                              {v}p
                            </button>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              </div>
              <div className="mt-4 p-3 bg-muted/20 border border-dashed border-border rounded-lg">
                <div className="text-[10px] text-muted-foreground leading-relaxed">
                  * 预处理将在云端 GPU 实例中异步执行，不占用当前浏览器资源。选中更多功能可能会略微增加任务分析耗时。
                </div>
              </div>
            </div>
          )}

          {currentStep === 3 && (
            <div className="space-y-4 animate-in fade-in duration-300">
              <h4 className="font-medium text-sm mb-4">多模态模型选择</h4>
              <div className="space-y-3 max-h-64 overflow-y-auto custom-scrollbar pr-2">
                {models
                  .filter(m => (m.capabilities || []).includes(preprocessOptions.recognition_mode))
                  .map(m => (
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
                    <div className="text-xs text-muted-foreground line-clamp-2 mb-2">{m.description}</div>
                    <div className="flex flex-wrap gap-1">
                      {(m.capabilities || []).map((cap: string) => (
                        <span key={cap} className={cn(
                          "text-[9px] px-1.5 py-0.5 rounded border flex items-center gap-1",
                          (preprocessOptions.recognition_mode === 'image' && cap === 'image') || (preprocessOptions.recognition_mode === 'video' && cap === 'video')
                            ? "bg-primary/20 border-primary/40 text-primary font-medium"
                            : "bg-muted border-border text-muted-foreground"
                        )}>
                          {cap === 'text' && '文本'}
                          {cap === 'image' && '图片'}
                          {cap === 'video' && '视频'}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
                {models.length === 0 && (
                  <div className="text-sm text-muted-foreground text-center py-8 border border-dashed border-border rounded-lg">
                    暂未配置任何模型，请前往“模型管理”添加。
                  </div>
                )}
                {models.length > 0 && models.filter(m => (m.capabilities || []).includes(preprocessOptions.recognition_mode)).length === 0 && (
                  <div className="text-sm text-muted-foreground text-center py-8 border border-dashed border-border rounded-lg">
                    当前识别模式（{preprocessOptions.recognition_mode === 'video' ? '视频' : '图片'}）下没有匹配的模型，请重新选择或前往“模型管理”配置。
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
      </motion.div>
    </div>
  );
}