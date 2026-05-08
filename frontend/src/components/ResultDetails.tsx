import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { Download, Play, ArrowLeft, Video, Scan, Loader2, X } from 'lucide-react';
import { domToPng } from 'modern-screenshot';
import { jsPDF } from 'jspdf';
import { cn } from '@/lib/utils';

export function ResultDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [selectedImg, setSelectedImg] = useState<string | null>(null);
  const reportRef = React.useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!id) return;
    fetch(`http://localhost:8000/tasks/${id}`)
      .then(r => r.json())
      .then(d => {
        setData(d);
        setLoading(false);
      })
      .catch(e => {
        console.error(e);
        setLoading(false);
      });
  }, [id]);

  const waitForImages = (container: HTMLElement): Promise<void> => {
    const images = container.querySelectorAll('img');
    return Promise.all(
      Array.from(images).map(img => {
        return new Promise<void>((resolve) => {
          if (img.complete && img.naturalWidth > 0) {
            resolve();
            return;
          }
          const timeout = setTimeout(resolve, 8000);
          const done = () => { clearTimeout(timeout); resolve(); };
          img.onload = done;
          img.onerror = done;
        });
      })
    ).then(() => {});
  };

  const handleExport = async () => {
    if (!reportRef.current || !data) return;
    setExporting(true);
    const original = reportRef.current;
    const originalRect = original.getBoundingClientRect();
    const captureWidth = Math.round(originalRect.width);
    
    if (captureWidth <= 0) {
      alert('无法获取报告宽度，请刷新页面后重试。');
      setExporting(false);
      return;
    }

    let overlay: HTMLDivElement | null = null;
    let container: HTMLDivElement | null = null;

    try {
      const clone = original.cloneNode(true) as HTMLElement;
      const originalCanvases = original.querySelectorAll('canvas');
      const clonedCanvases = clone.querySelectorAll('canvas');
      originalCanvases.forEach((origCanvas, idx) => {
        const clonedCanvas = clonedCanvases[idx];
        if (origCanvas && clonedCanvas) {
          const ctx = clonedCanvas.getContext('2d');
          if (ctx) ctx.drawImage(origCanvas, 0, 0);
        }
      });

      clone.classList.remove('overflow-y-auto', 'min-h-0', 'flex-1', 'custom-scrollbar');
      clone.style.cssText = `
        width: ${captureWidth}px;
        height: auto;
        max-height: none;
        overflow: visible;
        display: flex;
        gap: 1.5rem;
        padding: 0.5rem;
        box-sizing: border-box;
      `;

      const videos = clone.querySelectorAll('video');
      videos.forEach((video, idx) => {
        const videoEl = video as HTMLVideoElement;
        const parent = videoEl.parentElement;
        if (!parent) return;
        const framePath = idx === 0
          ? data.result?.key_frames_a?.[0]
          : data.result?.key_frames_b?.[0];
        if (framePath) {
          const cleanPath = framePath.replace(/\\/g, '/').replace(/^storage\//, '');
          const img = document.createElement('img');
          img.crossOrigin = 'anonymous';
          img.src = `http://localhost:8000/storage/${cleanPath}`;
          img.style.cssText = 'width: 100%; height: 100%; object-fit: contain;';
          parent.replaceChild(img, videoEl);
        }
      });

      const scrollContainers = clone.querySelectorAll('.overflow-x-auto');
      scrollContainers.forEach(el => {
        const htmlEl = el as HTMLElement;
        htmlEl.style.overflowX = 'visible';
        htmlEl.style.display = 'flex';
        htmlEl.style.flexWrap = 'wrap';
        htmlEl.style.gap = '8px';
      });

      overlay = document.createElement('div');
      overlay.style.cssText = 'position: fixed; inset: 0; z-index: 99998; background: rgba(15,23,42,0.95); display: flex; align-items: center; justify-content: center; color: #94a3b8; font-size: 16px;';
      overlay.textContent = '正在生成报告...';
      document.body.appendChild(overlay);

      container = document.createElement('div');
      container.style.cssText = `position: fixed; top: 0; left: 0; width: ${captureWidth}px; z-index: 99999; background: #0f172a;`;
      container.appendChild(clone);
      document.body.appendChild(container);

      void container.offsetHeight;
      await waitForImages(clone);
      await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)));

      const allImages = clone.querySelectorAll('img');
      await Promise.all(
        Array.from(allImages).map(async (img) => {
          try {
            const fetchUrl = new URL(img.src);
            fetchUrl.searchParams.set('t', Date.now().toString());
            const resp = await fetch(fetchUrl.toString(), { mode: 'cors', cache: 'no-cache' });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const blob = await resp.blob();
            const dataUrl = await new Promise<string>((resolve, reject) => {
              const reader = new FileReader();
              reader.onloadend = () => resolve(reader.result as string);
              reader.onerror = reject;
              reader.readAsDataURL(blob);
            });
            img.crossOrigin = 'anonymous';
            img.src = dataUrl;
            await img.decode().catch(() => {});
          } catch (e) {
            console.warn('Failed to fetch image as blob:', img.src, e);
          }
        })
      );

      await new Promise(resolve => setTimeout(resolve, 300));
      const captureHeight = clone.scrollHeight;
      const pngDataUrl = await domToPng(clone, {
        scale: 2,
        backgroundColor: '#0f172a',
        width: captureWidth,
        height: captureHeight,
      });

      const pdf = new jsPDF({
        orientation: captureWidth > captureHeight ? 'landscape' : 'portrait',
        unit: 'px',
        format: [captureWidth, captureHeight]
      });
      pdf.addImage(pngDataUrl, 'PNG', 0, 0, captureWidth, captureHeight);
      pdf.save(`EnvMatch_Report_${data.task.task_name || id}.pdf`);
    } catch (err) {
      console.error('Export failed:', err);
      alert(`导出报告失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      if (overlay && overlay.parentNode) document.body.removeChild(overlay);
      if (container && container.parentNode) document.body.removeChild(container);
      setExporting(false);
    }
  };

  if (loading) return <div className="p-8 text-center text-muted-foreground"><Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />加载中...</div>;
  if (!data || !data.task) return <div className="p-8 text-center text-red-500">获取任务详情失败</div>;

  const { task, result } = data;
  const isFailed = task.status === 'FAILED';
  const dims = result?.dimension_scores || {};

  const ringOption = {
    series: [
      {
        type: 'gauge',
        startAngle: 90,
        endAngle: -270,
        pointer: { show: false },
        progress: {
          show: true,
          overlap: false,
          roundCap: true,
          clip: false,
          itemStyle: {
            color: task.similarity_score > 70 ? '#10b981' : task.similarity_score > 40 ? '#f59e0b' : '#ef4444'
          }
        },
        axisLine: { lineStyle: { width: 8, color: [[1, 'rgba(255,255,255,0.05)']] } },
        splitLine: { show: false },
        axisTick: { show: false },
        axisLabel: { show: false },
        data: [{ value: task.similarity_score || 0 }],
        detail: { show: false }
      }
    ]
  };

  const radarOption = {
    backgroundColor: 'transparent',
    radar: {
      indicator: [
        { name: '光照/天气', max: 100 },
        { name: '建筑风格', max: 100 },
        { name: '固定设施', max: 100 },
        { name: '植被绿化', max: 100 },
        { name: '地面材质', max: 100 }
      ],
      shape: 'circle',
      splitNumber: 4,
      axisName: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: [
              dims.lighting_weather || 0,
              dims.architecture || 0,
              dims.facilities || 0,
              dims.vegetation || 0,
              dims.road_surface || 0
            ],
            name: '环境维度',
            symbol: 'none',
            itemStyle: { color: '#10b981' },
            areaStyle: { color: 'rgba(16, 185, 129, 0.2)' },
            lineStyle: { width: 2 }
          }
        ]
      }
    ]
  };

  return (
    <div className="p-6 h-full flex flex-col overflow-hidden bg-background">
      <div className="flex items-center justify-between mb-6 shrink-0">
        <h3 className="text-xl font-bold flex items-center gap-2">
          <button onClick={() => navigate('/tasks')} className="p-1.5 hover:bg-muted rounded-md transition-colors text-muted-foreground hover:text-foreground cursor-pointer">
            <ArrowLeft className="w-5 h-5" />
          </button>
          结果详情 <span className="text-sm text-muted-foreground font-normal">/ {id}</span>
        </h3>
        <div className="flex items-center gap-3">
          {isFailed && (
            <div className="px-3 py-1.5 rounded-md text-sm bg-red-500/10 text-red-500 border border-red-500/20 font-medium">
              任务执行失败，无法查看分析结果
            </div>
          )}
          <button
            onClick={handleExport}
            disabled={exporting || isFailed}
            className="bg-primary/20 text-primary hover:bg-primary/30 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 cursor-pointer transition-colors disabled:opacity-50"
          >
            {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />}
            {exporting ? '正在导出...' : '导出报告 (PDF)'}
          </button>
        </div>
      </div>

      <div ref={reportRef} className="flex gap-6 flex-1 min-h-0 overflow-y-auto custom-scrollbar p-2">
        {/* Left column */}
        <div className="w-[30%] flex flex-col items-center">
          <h4 className="text-sm font-medium w-full mb-4">相似度评分</h4>
          <div className="relative w-40 h-40 shrink-0 opacity-80">
            <ReactECharts option={ringOption} style={{ height: '100%', width: '100%' }} />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn("text-4xl font-bold", isFailed ? "text-muted-foreground" : "text-foreground")}>
                {isFailed ? '--' : (task.similarity_score?.toFixed(1) || 0)}
                {!isFailed && <span className="text-xl">%</span>}
              </span>
              <span className="text-xs text-red-500 mt-1">{isFailed ? '暂无评分' : '综合结果'}</span>
            </div>
          </div>
          
          <h4 className="text-sm font-medium w-full mt-8 mb-2">评分分布</h4>
          <div className={cn("w-full h-[260px] shrink-0", isFailed && "opacity-30 grayscale")}>
             <ReactECharts option={radarOption} style={{ height: '100%' }} />
          </div>
        </div>

        {/* Middle column: Video & Keyframes */}
        <div className="flex-1 min-w-0">
           <h4 className="text-sm font-medium mb-4">视频对比（关键帧对齐）</h4>
           <div className="flex gap-4">
             <div className="flex-1">
               <div className="text-xs text-muted-foreground mb-2">视频 A</div>
               <div className="bg-black aspect-video rounded-lg overflow-hidden">
                 <video
                   className="w-full h-full object-contain"
                   controls
                   preload="metadata"
                   src={`http://localhost:8000/storage/${(task.video_a_path || '').split('\\').pop()?.split('/').pop()}`}
                 >
                   您的浏览器不支持视频播放
                 </video>
               </div>
             </div>
             <div className="flex-1">
               <div className="text-xs text-muted-foreground mb-2">视频 B</div>
               <div className="bg-black aspect-video rounded-lg overflow-hidden">
                 <video
                   className="w-full h-full object-contain"
                   controls
                   preload="metadata"
                   src={`http://localhost:8000/storage/${(task.video_b_path || '').split('\\').pop()?.split('/').pop()}`}
                 >
                   您的浏览器不支持视频播放
                 </video>
               </div>
             </div>
           </div>

           <h4 className="text-sm font-medium mt-6 mb-2">环境要素抽取 (Keyframes)</h4>
           <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
             {result?.key_frames_a && result.key_frames_a.length > 0 ? result.key_frames_a.map((kf: string, i: number) => {
                const cleanPath = kf.replace(/\\/g, '/').replace(/^storage\//, '');
                return (
                   <div 
                     key={`a-${i}`} 
                     className="w-24 shrink-0 aspect-video bg-muted rounded overflow-hidden border border-border/50 cursor-pointer hover:border-primary/50 transition-colors"
                     onClick={() => setSelectedImg(`http://localhost:8000/storage/${cleanPath}`)}
                   >
                     <img
                       src={`http://localhost:8000/storage/${cleanPath}`}
                       className="w-full h-full object-cover"
                       alt={`Frame A${i}`}
                     />
                   </div>
                );
             }) : <span className="text-xs text-muted-foreground">{isFailed ? '任务执行失败，无法提取关键帧' : '视频 A 无关键帧数据'}</span>}
           </div>
           <div className="flex gap-2 overflow-x-auto mt-2 pb-2 custom-scrollbar">
             {result?.key_frames_b && result.key_frames_b.length > 0 ? result.key_frames_b.map((kf: string, i: number) => {
                const cleanPath = kf.replace(/\\/g, '/').replace(/^storage\//, '');
                return (
                   <div 
                     key={`b-${i}`} 
                     className="w-24 shrink-0 aspect-video bg-muted rounded overflow-hidden border border-border/50 cursor-pointer hover:border-primary/50 transition-colors"
                     onClick={() => setSelectedImg(`http://localhost:8000/storage/${cleanPath}`)}
                   >
                     <img
                       src={`http://localhost:8000/storage/${cleanPath}`}
                       className="w-full h-full object-cover"
                       alt={`Frame B${i}`}
                     />
                   </div>
                );
             }) : <span className="text-xs text-muted-foreground">{isFailed ? '任务执行失败，无法提取关键帧' : '视频 B 无关键帧数据'}</span>}
           </div>

           <div className="mt-6 border-t border-border pt-4">
             <div className="flex gap-6 mb-4 text-sm">
               <div className="text-primary font-medium cursor-pointer border-b border-primary pb-1">AI 综合分析</div>
             </div>
             
             <div className={cn("text-sm leading-relaxed p-4 rounded-lg border", isFailed ? "bg-red-500/5 text-red-500 border-red-500/10" : "bg-muted/20 text-foreground border-border/50")}>
                {isFailed ? (
                  <div className="flex flex-col gap-2">
                    <div className="font-bold flex items-center gap-2">
                      <X className="w-4 h-4" /> 错误信息：
                    </div>
                    <p className="font-mono text-xs opacity-80 whitespace-pre-wrap">
                      {result?.error_message || "模型调用发生未知错误，请检查网络或 API 配置。"}
                    </p>
                  </div>
                ) : (
                  result?.summary || '无描述信息'
                )}
             </div>

             {!isFailed && (
              <div className="grid grid-cols-2 gap-4 mt-4">
                <div className="bg-emerald-500/10 border border-emerald-500/20 p-3 rounded-lg">
                  <h5 className="text-xs font-bold text-emerald-500 mb-2">相似点</h5>
                  <ul className="text-xs text-foreground space-y-1 list-disc list-inside">
                    {result?.similar_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}
                  </ul>
                </div>
                <div className="bg-amber-500/10 border border-amber-500/20 p-3 rounded-lg">
                  <h5 className="text-xs font-bold text-amber-500 mb-2">差异点</h5>
                  <ul className="text-xs text-foreground space-y-1 list-disc list-inside">
                    {result?.difference_points?.map((p: string, i: number) => <li key={i}>{p}</li>)}
                  </ul>
                </div>
              </div>
             )}
           </div>
        </div>

        {/* Right column: Basics */}
        <div className="w-[25%] flex flex-col pl-4 border-l border-border/50 shrink-0">
          <h4 className="text-sm font-medium mb-4">基本信息</h4>
          <div className="space-y-3 text-xs bg-muted/20 p-4 rounded-lg">
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">任务名称:</span> <span className="font-medium text-base">{task.task_name}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">创建时间:</span> <span className="text-sm">{new Date(task.created_at).toLocaleString()}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">完成时间:</span> <span className="text-sm">{task.updated_at ? new Date(task.updated_at).toLocaleString() : '--'}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">耗时:</span> <span className="text-sm font-medium">{task.updated_at && task.created_at ? `${Math.round((new Date(task.updated_at).getTime() - new Date(task.created_at).getTime()) / 1000)} 秒` : '--'}</span></div>
            <div className="flex flex-col gap-1"><span className="text-muted-foreground">模型版本:</span> <span className="text-sm">{task.model_id || 'Gemini 1.5 Pro'}</span></div>
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground">识别方式:</span>
              <div className="flex items-center gap-1.5 mt-0.5">
                {task.preprocess_options?.recognition_mode === 'video' ? (
                  <>
                    <Video className="w-3.5 h-3.5 text-blue-500" />
                    <span className="text-sm font-medium text-blue-500">视频 LLM 识别</span>
                  </>
                ) : (
                  <>
                    <Scan className="w-3.5 h-3.5 text-purple-500" />
                    <span className="text-sm font-medium text-purple-500">图片 LLM 识别</span>
                  </>
                )}
              </div>
            </div>
            <div className="flex flex-col gap-1 border-t border-border/50 pt-2 mt-2">
              <div className="flex justify-between items-center"><span className="text-muted-foreground">视频时长:</span> <span className="text-sm font-medium">{(task.video_a_duration || 0).toFixed(1)}s / {(task.video_b_duration || 0).toFixed(1)}s</span></div>
              <div className="flex justify-between items-center"><span className="text-muted-foreground">分辨率:</span> <span className="text-sm font-medium">{task.video_a_resolution || '--'} / {task.video_b_resolution || '--'}</span></div>
              <div className="flex justify-between items-center"><span className="text-muted-foreground">文件大小:</span> <span className="text-sm font-medium">{task.video_a_size || 0}MB / {task.video_b_size || 0}MB</span></div>
              <div className="flex justify-between items-center"><span className="text-muted-foreground">关键帧数量:</span> <span className="text-sm font-medium">{(result?.key_frames_a || []).length} / {(result?.key_frames_b || []).length}</span></div>
            </div>
            <div className="flex flex-col gap-1 border-t border-border/50 pt-2 mt-2">
              <div className="flex justify-between items-center"><span className="text-muted-foreground">输入 Token:</span> <span className="text-sm font-medium">{task.input_tokens || 0}</span></div>
              <div className="flex justify-between items-center"><span className="text-muted-foreground">输出 Token:</span> <span className="text-sm font-medium">{task.output_tokens || 0}</span></div>
            </div>
          </div>
        </div>
      </div>
      
      {selectedImg && (
        <ImagePreviewModal src={selectedImg} onClose={() => setSelectedImg(null)} />
      )}
    </div>
  );
}

function ImagePreviewModal({ src, onClose }: { src: string; onClose: () => void }) {
  const [zoom, setZoom] = useState(1);
  const [position, setPosition] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const [isMaximized, setIsMaximized] = useState(false);
  const imgRef = React.useRef<HTMLImageElement>(null);

  const toggleMaximize = (e: React.MouseEvent) => {
    e.stopPropagation();
    setIsMaximized(!isMaximized);
    setPosition({ x: 0, y: 0 });
    setZoom(1);
  };

  const handleWheel = (e: React.WheelEvent) => {
    e.stopPropagation();
    const delta = e.deltaY > 0 ? -0.2 : 0.2;
    setZoom(prev => Math.min(Math.max(0.5, prev + delta), 5));
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (zoom <= 1) return;
    e.preventDefault();
    setIsDragging(true);
    setDragStart({ x: e.clientX - position.x, y: e.clientY - position.y });
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return;
    setPosition({
      x: e.clientX - dragStart.x,
      y: e.clientY - dragStart.y
    });
  };

  const handleMouseUp = () => setIsDragging(false);

  const reset = (e: React.MouseEvent) => {
    e.stopPropagation();
    setZoom(1);
    setPosition({ x: 0, y: 0 });
  };

  return (
    <div 
      className="fixed inset-0 bg-background/80 backdrop-blur-sm z-[100000] flex items-center justify-center p-0 sm:p-4 animate-in fade-in duration-300"
      onClick={onClose}
    >
      <div 
        className={cn(
          "bg-card border border-border flex flex-col overflow-hidden animate-in zoom-in duration-300 transition-all",
          isMaximized 
            ? "fixed inset-0 w-screen h-screen max-w-none rounded-none z-[100001]" 
            : "relative rounded-2xl shadow-2xl w-full max-w-4xl max-h-[90vh]"
        )}
        onClick={e => e.stopPropagation()}
        onWheel={handleWheel}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
      >
        <div className="px-6 py-4 border-b border-border flex justify-between items-center bg-muted/30">
          <div className="flex items-center gap-3">
            <button 
              onClick={toggleMaximize}
              className="w-10 h-10 rounded-xl bg-primary/10 flex items-center justify-center border border-primary/20 hover:bg-primary/20 transition-all group"
            >
              <Scan className={cn("w-5 h-5 text-primary transition-transform", isMaximized ? "scale-125" : "group-hover:scale-110")} />
            </button>
            <div>
              <h4 className="font-bold text-base text-foreground">关键帧预览</h4>
              <p className="text-[10px] text-muted-foreground uppercase tracking-wider">
                {isMaximized ? 'Full Screen Mode' : 'Keyframe Detail View'}
              </p>
            </div>
          </div>
          
          <div className="flex items-center gap-3">
            <div className="flex bg-muted/50 rounded-lg border border-border p-1">
              <button onClick={() => setZoom(prev => Math.max(0.5, prev - 0.5))} className="p-1.5 hover:bg-background rounded text-muted-foreground hover:text-primary transition-all">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 12H4" /></svg>
              </button>
              <div className="w-12 flex items-center justify-center text-xs text-foreground font-mono font-medium">{Math.round(zoom * 100)}%</div>
              <button onClick={() => setZoom(prev => Math.min(5, prev + 0.5))} className="p-1.5 hover:bg-background rounded text-muted-foreground hover:text-primary transition-all">
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" /></svg>
              </button>
            </div>
            <button onClick={reset} className="p-2 bg-muted/50 rounded-lg border border-border text-muted-foreground hover:text-primary hover:border-primary/50 transition-all">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" /></svg>
            </button>
            <div className="w-px h-6 bg-border mx-1" />
            <button onClick={onClose} className="p-2 hover:bg-red-500/10 hover:text-red-500 rounded-full transition-all">
              <X className="w-5 h-5"/>
            </button>
          </div>
        </div>

        <div className={cn("relative p-2 bg-muted/10 overflow-hidden flex items-center justify-center flex-1", isMaximized ? "h-full" : "min-h-[500px] max-h-[70vh]")}>
          <div 
            className={`relative transition-transform duration-200 ease-out cursor-grab ${isDragging ? 'cursor-grabbing' : ''}`}
            style={{ transform: `translate(${position.x}px, ${position.y}px) scale(${zoom})` }}
            onMouseDown={handleMouseDown}
          >
            <img ref={imgRef} src={src} className="max-w-full max-h-full object-contain shadow-sm pointer-events-none rounded" alt="Preview" />
          </div>
          <div className="absolute bottom-4 left-6 text-[10px] text-muted-foreground italic">提示：滚轮可缩放，放大后可鼠标拖动查看详情</div>
        </div>
        
        <div className="px-6 py-3 bg-muted/30 border-t border-border flex justify-end">
          <button onClick={onClose} className="bg-primary text-primary-foreground px-6 py-2 rounded-xl text-sm font-bold hover:bg-primary/90 transition-all shadow-lg shadow-primary/20">关闭预览</button>
        </div>
      </div>
    </div>
  );
}
