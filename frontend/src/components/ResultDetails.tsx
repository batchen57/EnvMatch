import React, { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import ReactECharts from 'echarts-for-react';
import { Download, Play, ArrowLeft, Video, Scan, Loader2, X, AlertCircle, Clock, Zap, FileText, Hash, Calendar, Layers } from 'lucide-react';
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
                 <video className="w-full h-full object-contain" controls src={`http://localhost:8000/storage/${(task.video_a_path || '').split('\\').pop()?.split('/').pop()}`} />
               </div>
               <div className="flex-1 bg-black aspect-video rounded-lg overflow-hidden border border-border/50">
                 <video className="w-full h-full object-contain" controls src={`http://localhost:8000/storage/${(task.video_b_path || '').split('\\').pop()?.split('/').pop()}`} />
               </div>
             </div>
           </div>

           <div>
             <h4 className="text-sm font-medium mb-4">环境要素抽取 (Keyframes)</h4>
             <div className="space-y-2">
               <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                 {result?.key_frames_a?.map((kf: string, i: number) => (
                   <img key={i} src={`http://localhost:8000/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`} 
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => setSelectedImg(`http://localhost:8000/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`)} />
                 ))}
               </div>
               <div className="flex gap-2 overflow-x-auto pb-2 custom-scrollbar">
                 {result?.key_frames_b?.map((kf: string, i: number) => (
                   <img key={i} src={`http://localhost:8000/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`} 
                        className="w-24 aspect-video object-cover rounded border border-border hover:border-primary cursor-pointer transition-all"
                        onClick={() => setSelectedImg(`http://localhost:8000/storage/${kf.replace(/\\/g, '/').replace(/^storage\//, '')}`)} />
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

        {/* Right: Basics (FULL RESTORED) */}
        <div className="w-[280px] flex flex-col pl-4 border-l border-border/50 shrink-0 space-y-6">
          <h4 className="text-sm font-medium">基本信息</h4>
          <div className="space-y-4 text-xs">
            <InfoBlock label="任务名称" val={task.task_name} icon={<FileText className="w-3.5 h-3.5"/>} />
            <InfoBlock label="任务ID" val={task.id} icon={<Hash className="w-3.5 h-3.5"/>} isMono />
            <InfoBlock label="创建时间" val={new Date(task.created_at).toLocaleString()} icon={<Calendar className="w-3.5 h-3.5"/>} />
            <InfoBlock label="完成时间" val={task.updated_at ? new Date(task.updated_at).toLocaleString() : '--'} icon={<Clock className="w-3.5 h-3.5"/>} />
            <InfoBlock label="模型版本" val={task.model_id} icon={<Zap className="w-3.5 h-3.5"/>} isMono />
            
            <div className="pt-4 border-t border-border/50 space-y-2">
              <div className="text-[10px] font-bold text-muted-foreground uppercase">视频规格参数</div>
              <SpecRow label="视频 A 分辨率" val={task.video_a_resolution} />
              <SpecRow label="视频 B 分辨率" val={task.video_b_resolution} />
              <SpecRow label="视频 A 时长" val={task.video_a_duration ? `${task.video_a_duration.toFixed(1)}s` : '--'} />
              <SpecRow label="视频 B 时长" val={task.video_b_duration ? `${task.video_b_duration.toFixed(1)}s` : '--'} />
              <SpecRow label="视频 A 大小" val={task.video_a_size ? `${task.video_a_size}MB` : '--'} />
              <SpecRow label="视频 B 大小" val={task.video_b_size ? `${task.video_b_size}MB` : '--'} />
            </div>

            <div className="pt-4 border-t border-border/50 space-y-2">
              <div className="text-[10px] font-bold text-muted-foreground uppercase">资源消耗统计</div>
              <SpecRow label="Input Tokens" val={task.input_tokens || 0} />
              <SpecRow label="Output Tokens" val={task.output_tokens || 0} />
            </div>
          </div>
        </div>
      </div>
      
      {selectedImg && (
        <div className="fixed inset-0 bg-black/90 backdrop-blur-sm z-[100000] flex items-center justify-center p-8" onClick={()=>setSelectedImg(null)}>
           <img src={selectedImg} className="max-w-full max-h-full rounded shadow-2xl" />
           <button className="absolute top-8 right-8 text-white/50 hover:text-white"><X className="w-8 h-8"/></button>
        </div>
      )}
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

function InfoBlock({ label, val, icon, isMono }: any) {
  return (
    <div className="flex gap-3">
      <div className="p-2 bg-muted rounded-lg shrink-0">{icon}</div>
      <div className="min-w-0 flex-1">
        <div className="text-[10px] text-muted-foreground font-medium uppercase tracking-wider">{label}</div>
        <div className={cn("text-xs font-bold truncate", isMono && "font-mono")}>{val || '--'}</div>
      </div>
    </div>
  );
}

function SpecRow({ label, val }: any) {
  return (
    <div className="flex justify-between text-[11px]">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{val || '--'}</span>
    </div>
  );
}
