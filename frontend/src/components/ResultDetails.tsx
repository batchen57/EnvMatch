import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { Download, Play, ArrowLeft, Video, Scan, Loader2, X, AlertCircle, Clock, Zap, FileText, Hash, Calendar, Layers, Minus, Plus, RotateCcw } from 'lucide-react';
import { domToPng } from 'modern-screenshot';
import { jsPDF } from 'jspdf';
import { cn } from '@/lib/utils';
import { motion, AnimatePresence } from 'framer-motion';

export function ResultDetails() {
  const navigate = useNavigate();
  const { id } = useParams();
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [selectedImg, setSelectedImg] = useState<string | null>(null);
  const [zoom, setZoom] = useState(100);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const reportRef = React.useRef<HTMLDivElement>(null);
  const previewRef = React.useRef<HTMLDivElement>(null);

  const toggleFullScreen = () => {
    if (!previewRef.current) return;
    if (!document.fullscreenElement) {
      previewRef.current.requestFullscreen().catch(err => {
        console.error(`Error attempting to enable full-screen mode: ${err.message}`);
      });
    } else {
      document.exitFullscreen();
    }
  };

  useEffect(() => {
    if (!id) return;
    fetch(`http://localhost:8888/tasks/${id}`)
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
          if (img.complete && img.naturalWidth > 0) resolve();
          else {
            const timeout = setTimeout(resolve, 8000);
            const done = () => { clearTimeout(timeout); resolve(); };
            img.onload = done;
            img.onerror = done;
          }
        });
      })
    ).then(() => {});
  };

  const handleExport = async () => {
    if (!reportRef.current || !data) return;
    setExporting(true);
    const original = reportRef.current;
    try {
      const clone = original.cloneNode(true) as HTMLElement;
      clone.style.width = '1000px';
      clone.style.height = 'auto';
      clone.style.background = '#0f172a';
      document.body.appendChild(clone);
      await waitForImages(clone);
      const pngDataUrl = await domToPng(clone, { scale: 2, backgroundColor: '#0f172a' });
      const pdf = new jsPDF({ orientation: 'p', unit: 'px', format: [1000, clone.scrollHeight] });
      pdf.addImage(pngDataUrl, 'PNG', 0, 0, 1000, clone.scrollHeight);
      pdf.save(`EnvMatch_${data.task.task_name || id}.pdf`);
      document.body.removeChild(clone);
    } catch (err) {
      alert(`导出失败: ${err}`);
    } finally {
      setExporting(false);
    }
  };

  if (loading) return <div className="p-8 text-center text-muted-foreground"><Loader2 className="w-8 h-8 animate-spin mx-auto mb-2" />加载中...</div>;
  if (!data || !data.task) return <div className="p-8 text-center text-red-500">获取任务详情失败</div>;

  const { task, result } = data;
  const isFailed = task.status === 'FAILED';
  const dims = result?.dimension_scores || {};

  const ringOption = {
    series: [{
      type: 'gauge',
      startAngle: 90, endAngle: -270,
      pointer: { show: false },
      progress: {
        show: true, overlap: false, roundCap: true,
        itemStyle: { color: task.similarity_score > 70 ? '#10b981' : task.similarity_score > 40 ? '#f59e0b' : '#ef4444' }
      },
      axisLine: { lineStyle: { width: 8, color: [[1, 'rgba(255,255,255,0.05)']] } },
      splitLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false },
      data: [{ value: task.similarity_score || 0 }],
      detail: { show: false }
    }]
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
      shape: 'circle', splitNumber: 4,
      axisName: { color: '#94a3b8', fontSize: 10 },
      splitLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } },
      splitArea: { show: false },
      axisLine: { lineStyle: { color: 'rgba(148, 163, 184, 0.1)' } }
    },
    series: [{
      type: 'radar',
      data: [{
        value: [
          dims.lighting_weather || 0,
          dims.architecture || 0,
          dims.facilities || 0,
          dims.vegetation || 0,
          dims.road_surface || 0
        ],
        symbol: 'none',
        itemStyle: { color: '#10b981' },
        areaStyle: { color: 'rgba(16, 185, 129, 0.2)' },
        lineStyle: { width: 2 }
      }]
    }]
  };

  return (
    <div className="p-6 h-full flex flex-col overflow-hidden bg-background">
      <div className="flex items-center justify-between mb-6 shrink-0">
        <h3 className="text-xl font-bold flex items-center gap-2">
          <button onClick={() => navigate('/tasks')} className="p-1.5 hover:bg-muted rounded-md transition-colors"><ArrowLeft className="w-5 h-5" /></button>
          结果详情 <span className="text-sm text-muted-foreground font-normal">/ {id}</span>
        </h3>
        <button onClick={handleExport} disabled={exporting} className="bg-primary/20 text-primary hover:bg-primary/30 px-3 py-1.5 rounded-md text-sm flex items-center gap-2 transition-colors">
          {exporting ? <Loader2 className="w-4 h-4 animate-spin" /> : <Download className="w-4 h-4" />} 导出报告 (PDF)
        </button>
      </div>

      <div ref={reportRef} className="flex gap-6 flex-1 min-h-0 overflow-y-auto custom-scrollbar p-2">
        {/* Left: Scoring */}
        <div className="w-[280px] flex flex-col items-center shrink-0">
          <h4 className="text-sm font-medium w-full mb-4">相似度评分</h4>
          <div className="relative w-44 h-44 shrink-0">
            <ReactECharts option={ringOption} style={{ height: '100%', width: '100%' }} />
            <div className="absolute inset-0 flex flex-col items-center justify-center">
              <span className={cn("text-4xl font-bold", isFailed && !task.similarity_score ? "text-muted-foreground" : "text-foreground")}>
                {task.similarity_score?.toFixed(1) || '--'}
                {task.similarity_score > 0 && <span className="text-xl">%</span>}
              </span>
              <span className="text-xs text-primary mt-1">{isFailed ? '暂无评分' : '综合结果'}</span>
            </div>
          </div>
          
          <h4 className="text-sm font-medium w-full mt-8 mb-4">评分分布</h4>
          <div className="w-full h-[240px] shrink-0 mb-4">
             <ReactECharts option={radarOption} style={{ height: '100%' }} />
          </div>
          <div className="w-full space-y-2">
             <ScoreRow label="光照/天气" val={dims.lighting_weather} />
             <ScoreRow label="建筑风格" val={dims.architecture} />
             <ScoreRow label="固定设施" val={dims.facilities} />
             <ScoreRow label="植被绿化" val={dims.vegetation} />
             <ScoreRow label="地面材质" val={dims.road_surface} />
          </div>
        </div>

        {/* Middle: Video & Content */}
        <div className="flex-1 min-w-0 space-y-6">
           <div>
             <h4 className="text-sm font-medium mb-4">视频对比（关键帧对齐）</h4>
             <div className="flex gap-4">
               <div className="flex-1 bg-black aspect-video rounded-lg overflow-hidden border border-border/50">
                 <video className="w-full h-full object-contain" controls src={`http://localhost:8888/storage/${(task.video_a_path || '').split('\\').pop()?.split('/').pop()}`} />
               </div>
               <div className="flex-1 bg-black aspect-video rounded-lg overflow-hidden border border-border/50">
                 <video className="w-full h-full object-contain" controls src={`http://localhost:8888/storage/${(task.video_b_path || '').split('\\').pop()?.split('/').pop()}`} />
               </div>
             </div>
           </div>

           <div>
             <h4 className="text-sm font-medium mb-4">环境要素抽取 (Keyframes)</h4>
             <div className="space-y-2">
               <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                 {result?.key_frames_a?.map((kf: string, i: number) => (
                   <img key={i} src={`http://localhost:8888/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`} 
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => {
                          setSelectedImg(`http://localhost:8888/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`);
                          setZoom(100);
                          setPos({ x: 0, y: 0 });
                        }} />
                 ))}
               </div>
               <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                 {result?.key_frames_b?.map((kf: string, i: number) => (
                   <img key={i} src={`http://localhost:8888/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`} 
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => {
                          setSelectedImg(`http://localhost:8888/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`);
                          setZoom(100);
                          setPos({ x: 0, y: 0 });
                        }} />
                 ))}
               </div>
             </div>
           </div>

           <div>
             <h4 className="text-sm font-medium mb-4">AI 综合分析结论</h4>
             <div className={cn("text-sm leading-relaxed p-4 rounded-lg border", isFailed ? "bg-red-500/5 text-red-500 border-red-500/10" : "bg-muted/20 border-border/50")}>
                <div className="whitespace-pre-wrap">{result?.summary || "等待分析中..."}</div>
             </div>
             <div className="grid grid-cols-2 gap-4 mt-4">
                <div className="bg-emerald-500/5 border border-emerald-500/10 p-3 rounded-lg">
                  <h5 className="text-xs font-bold text-emerald-500 mb-2">相似点</h5>
                  <ul className="text-xs space-y-1 list-disc list-inside opacity-80">{result?.similar_points?.map((p:string,i:number)=><li key={i}>{p}</li>)}</ul>
                </div>
                <div className="bg-amber-500/5 border border-amber-500/10 p-3 rounded-lg">
                  <h5 className="text-xs font-bold text-amber-500 mb-2">差异点</h5>
                  <ul className="text-xs space-y-1 list-disc list-inside opacity-80">{result?.difference_points?.map((p:string,i:number)=><li key={i}>{p}</li>)}</ul>
                </div>
              </div>
           </div>
        </div>

        {/* Right: Basics (Redesigned according to mockup) */}
        <div className="w-[300px] flex flex-col pl-4 border-l border-border/30 shrink-0">
          <h4 className="text-sm font-bold mb-4 text-foreground/90">基本信息</h4>
          
          <div className="bg-[#0b0f1a] rounded-xl p-5 border border-white/5 space-y-5 shadow-2xl">
            {/* Top Section: Task Info */}
            <div className="space-y-4">
              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">任务名称:</div>
                <div className="text-lg font-bold text-foreground tracking-tight">{task.task_name}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">创建时间:</div>
                <div className="text-[13px] font-medium text-foreground/90">{new Date(task.created_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '/')}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">完成时间:</div>
                <div className="text-[13px] font-medium text-foreground/90">{task.updated_at ? new Date(task.updated_at).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '/') : '--'}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">耗时:</div>
                <div className="text-[15px] font-bold text-foreground">
                  {task.updated_at ? `${Math.round((new Date(task.updated_at).getTime() - new Date(task.created_at).getTime()) / 1000)} 秒` : '--'}
                </div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">模型版本:</div>
                <div className="text-[13px] font-bold text-foreground font-mono">{task.model_id}</div>
              </div>

              <div className="space-y-1">
                <div className="text-[11px] text-muted-foreground/60">识别方式:</div>
                <div className="flex items-center gap-1.5 text-blue-400 font-medium text-[13px]">
                  <Video className="w-4 h-4" /> 视频 LLM 识别
                </div>
              </div>
            </div>

            <div className="h-px bg-white/5" />

            {/* Middle Section: Video Specs */}
            <div className="space-y-2.5">
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">视频时长:</span>
                <span className="text-foreground font-medium">{task.video_a_duration?.toFixed(1)}s / {task.video_b_duration?.toFixed(1)}s</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">分辨率:</span>
                <span className="text-foreground font-medium">{task.video_a_resolution} / {task.video_b_resolution}</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">文件大小:</span>
                <span className="text-foreground font-medium">{task.video_a_size}MB / {task.video_b_size}MB</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <span className="text-muted-foreground/60">关键帧数量:</span>
                <span className="text-foreground font-medium">{(result?.key_frames_a || []).length} / {(result?.key_frames_b || []).length}</span>
              </div>
            </div>

            <div className="h-px bg-white/5" />

            {/* Bottom Section: Token Usage */}
            <div className="space-y-2.5">
              <div className="flex justify-between items-center text-[12px]">
                <div className="flex items-center gap-1.5">
                   <div className="w-1 h-1 rounded-full bg-blue-500/50" />
                   <span className="text-muted-foreground/60">输入 Token:</span>
                </div>
                <span className="text-foreground font-bold font-mono">{task.input_tokens || 0}</span>
              </div>
              <div className="flex justify-between items-center text-[12px]">
                <div className="flex items-center gap-1.5">
                   <div className="w-1 h-1 rounded-full bg-emerald-500/50" />
                   <span className="text-muted-foreground/60">输出 Token:</span>
                </div>
                <span className="text-foreground font-bold font-mono">{task.output_tokens || 0}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <AnimatePresence>
        {selectedImg && (
          <motion.div 
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/80 backdrop-blur-md z-[100000] flex items-center justify-center p-8"
            onClick={() => setSelectedImg(null)}
          >
            <motion.div 
              ref={previewRef}
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="bg-[#1a1f2e]/95 border border-white/10 rounded-2xl w-full max-w-5xl h-[85vh] flex flex-col overflow-hidden shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              {/* Header */}
              <div className="flex items-center justify-between px-6 py-4 border-b border-white/5">
                <div className="flex items-center gap-3">
                  <button 
                    onClick={toggleFullScreen}
                    className="p-2 bg-blue-500/10 rounded-lg hover:bg-blue-500/20 transition-colors cursor-pointer group"
                    title="全屏展示"
                  >
                    <Scan className="w-5 h-5 text-blue-400 group-hover:scale-110 transition-transform" />
                  </button>
                  <div>
                    <div className="text-sm font-bold text-white">关键帧预览</div>
                    <div className="text-[10px] text-white/40 tracking-wider">KEYFRAME DETAIL VIEW</div>
                  </div>
                </div>
                
                <div className="flex items-center gap-4">
                  <div className="flex items-center gap-2 bg-white/5 px-3 py-1 rounded-full border border-white/5">
                    <button 
                      onClick={() => setZoom(z => Math.max(20, z - 10))} 
                      className="p-1 hover:text-white text-white/50 transition-colors"
                    >
                      <Minus className="w-4 h-4" />
                    </button>
                    <span className="text-xs font-mono text-white/80 w-12 text-center">{zoom}%</span>
                    <button 
                      onClick={() => setZoom(z => Math.min(500, z + 10))} 
                      className="p-1 hover:text-white text-white/50 transition-colors"
                    >
                      <Plus className="w-4 h-4" />
                    </button>
                  </div>
                  <button 
                    onClick={() => { setZoom(100); setPos({x:0, y:0}); }} 
                    className="p-2 hover:bg-white/5 rounded-full text-white/50 hover:text-white transition-colors"
                  >
                    <RotateCcw className="w-4 h-4" />
                  </button>
                  <div className="w-px h-4 bg-white/10 mx-1" />
                  <button 
                    onClick={() => setSelectedImg(null)} 
                    className="p-2 hover:bg-red-500/10 rounded-full text-white/50 hover:text-red-400 transition-colors"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>

              {/* Content */}
              <div 
                className="flex-1 relative overflow-hidden bg-[#0a0d14] flex items-center justify-center cursor-grab active:cursor-grabbing"
                onWheel={(e) => {
                  const delta = e.deltaY > 0 ? -10 : 10;
                  setZoom(z => Math.min(500, Math.max(20, z + delta)));
                }}
              >
                 <motion.img 
                   src={selectedImg} 
                   animate={{ 
                     scale: zoom / 100,
                     x: pos.x,
                     y: pos.y
                   }}
                   transition={{ type: 'spring', damping: 25, stiffness: 200 }}
                   drag
                   onDrag={(e, info) => {
                     setPos(prev => ({
                       x: prev.x + info.delta.x,
                       y: prev.y + info.delta.y
                     }));
                   }}
                   className="max-w-[90%] max-h-[90%] object-contain shadow-2xl pointer-events-none select-none"
                 />
              </div>

              {/* Footer */}
              <div className="px-6 py-4 flex items-center justify-between border-t border-white/5 bg-[#1a1f2e]/50 backdrop-blur-sm">
                <div className="text-[11px] text-white/40 italic flex items-center gap-2">
                  提示: 滚轮可缩放，放大后可鼠标拖动查看详情
                </div>
                <button 
                  onClick={() => setSelectedImg(null)}
                  className="px-6 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-sm font-medium transition-all shadow-lg shadow-blue-600/20"
                >
                  关闭预览
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

function ScoreRow({ label, val }: { label: string; val: number }) {
  return (
    <div className="flex justify-between items-center text-xs">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-bold text-primary">{val || 0}%</span>
    </div>
  );
}
